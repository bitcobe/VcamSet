package com.vcamset;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_SELECT_MP4 = 1001;
    private static final int REQUEST_CODE_SELECT_TARGET_DIR = 1003;

    private static final String PREFS_NAME = "VCamSetPrefs";
    private static final String KEY_TARGET_PATH = "target_path";

    private Button btnLoad, btnSetTarget;
    private TextView tvTargetPath;
    private ImageView ivVideoPreview;

    private String targetPath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnLoad = (Button) findViewById(R.id.btnLoad);
        btnSetTarget = (Button) findViewById(R.id.btnSetTarget);
        tvTargetPath = (TextView) findViewById(R.id.tvTargetPath);
        ivVideoPreview = (ImageView) findViewById(R.id.ivVideoPreview);

        loadSavedPath();
        updateUIState();

        btnLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMp4Picker();
            }
        });

        btnSetTarget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFolderPicker(REQUEST_CODE_SELECT_TARGET_DIR);
            }
        });
    }

    private void loadSavedPath() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        targetPath = prefs.getString(KEY_TARGET_PATH, "");

        if (!targetPath.isEmpty()) {
            tvTargetPath.setText("Target path: " + targetPath);
        }
    }

    private void savePath() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_TARGET_PATH, targetPath);
        editor.commit();
    }

    private void updateUIState() {
        boolean isReady = !targetPath.isEmpty();
        btnLoad.setEnabled(isReady);
        if (isReady) {
            btnLoad.setBackgroundColor(0xFF2196F3);
        } else {
            btnLoad.setBackgroundColor(0xFFBDBDBD);
        }
    }

    private void openFolderPicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Izaberi Vcam target folder"), requestCode);
        } catch (Exception e) {
            Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openMp4Picker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/mp4");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Izaberi MP4 fajl"), REQUEST_CODE_SELECT_MP4);
        } catch (Exception e) {
            Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                if (requestCode == REQUEST_CODE_SELECT_TARGET_DIR) {
                    String path = getRealFilePathFromUri(uri);
                    if (path != null) {
                        File f = new File(path);
                        targetPath = f.isDirectory() ? f.getAbsolutePath() : f.getParent();
                    } else {
                        targetPath = uri.getPath();
                    }
                    tvTargetPath.setText("Target path: " + targetPath);
                    savePath();
                    updateUIState();
                } else if (requestCode == REQUEST_CODE_SELECT_MP4) {
                    processAndReplaceVcamFiles(uri);
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void processAndReplaceVcamFiles(Uri sourceUri) {
        try {
            if (targetPath.isEmpty()) {
                Toast.makeText(this, "Target folder nije podešen!", Toast.LENGTH_SHORT).show();
                return;
            }

            String sourceFilePath = getRealFilePathFromUri(sourceUri);
            if (sourceFilePath == null || !new File(sourceFilePath).exists()) {
                Toast.makeText(this, "Ne mogu da pronađem izabrani MP4 fajl!", Toast.LENGTH_SHORT).show();
                return;
            }

            File selectedVideoFile = new File(sourceFilePath);
            File sourceDir = selectedVideoFile.getParentFile();
            String videoFileNameWithoutExt = getFileNameWithoutExtension(selectedVideoFile.getName());

            // 1. Kopiramo fajl u privremeni lokalni keš aplikacije radi stabilnosti
            File cacheVideoFile = new File(getCacheDir(), "temp_selected.mp4");
            copyUriToFile(sourceUri, cacheVideoFile);

            // 2. Generišemo preview
            showVideoPreviewFromCache(cacheVideoFile);

            // 3. Kopiramo virtual.mp4 u target folder preko roota
            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                executeRootCommand("mkdir -p '" + targetDir.getAbsolutePath() + "'");
            }

            File destinationVideoFile = new File(targetDir, "virtual.mp4");
            boolean videoSuccess = executeRootCommand("cp -f '" + cacheVideoFile.getAbsolutePath() + "' '" + destinationVideoFile.getAbsolutePath() + "'");

            if (videoSuccess && destinationVideoFile.exists()) {
                executeRootCommand("chmod 666 '" + destinationVideoFile.getAbsolutePath() + "'");
            }

            // 4. Detekcija prateće slike (PNG ima prednost nad JPG) i direktno kopiranje kao 1000.bmp
            File companionImageFile = null;
            if (sourceDir != null && sourceDir.exists()) {
                File pngCandidate = new File(sourceDir, videoFileNameWithoutExt + ".png");
                File jpgCandidate = new File(sourceDir, videoFileNameWithoutExt + ".jpg");

                if (pngCandidate.exists()) {
                    companionImageFile = pngCandidate;
                } else if (jpgCandidate.exists()) {
                    companionImageFile = jpgCandidate;
                }
            }

            if (companionImageFile != null && companionImageFile.exists()) {
                File destinationBmpFile = new File(targetDir, "1000.bmp");
                boolean bmpSuccess = executeRootCommand("cp -f '" + companionImageFile.getAbsolutePath() + "' '" + destinationBmpFile.getAbsolutePath() + "'");
                
                if (bmpSuccess && destinationBmpFile.exists()) {
                    executeRootCommand("chmod 666 '" + destinationBmpFile.getAbsolutePath() + "'");
                    Toast.makeText(this, "Uspešno prekopirani virtual.mp4 i 1000.bmp!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Video prekopiran, ali greška sa 1000.bmp!", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Uspešno kreiran virtual.mp4 (slika nije pronađena)", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void copyUriToFile(Uri uri, File destination) throws Exception {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        OutputStream outputStream = new FileOutputStream(destination);
        byte[] buffer = new byte[4096];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }
        outputStream.flush();
        outputStream.close();
        inputStream.close();
    }

    private String getFileNameWithoutExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) return fileName;
        return fileName.substring(0, dotIndex);
    }

    private void showVideoPreviewFromCache(File file) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(file.getAbsolutePath());
            Bitmap bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            retriever.release();

            if (bitmap != null) {
                ivVideoPreview.setImageBitmap(bitmap);
                ivVideoPreview.setVisibility(View.VISIBLE);
            } else {
                ivVideoPreview.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            e.printStackTrace();
            ivVideoPreview.setVisibility(View.GONE);
        }
    }

    private boolean executeRootCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(process.getOutputStream());
            outputStream.writeBytes(command + "\n");
            outputStream.writeBytes("exit\n");
            outputStream.flush();
            outputStream.close();
            
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String getRealFilePathFromUri(Uri uri) {
        String path = uri.getPath();
        if (path != null) {
            if (path.startsWith("/external_files/")) {
                return path.replace("/external_files/", "/storage/emulated/0/");
            } else if (path.contains("/document/primary:")) {
                return path.replace("/document/primary:", "/storage/emulated/0/");
            } else if (new File(path).exists()) {
                return path;
            }
        }
        return uri.getPath();
    }
}
