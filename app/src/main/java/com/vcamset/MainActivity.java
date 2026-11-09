package com.vcamset;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;

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

        // Dugme "Load" otvara birač MP4 fajlova
        btnLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMp4Picker();
            }
        });

        // Dugme za postavljanje ciljnog Vcam foldera
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
            btnLoad.setBackgroundColor(0xFF2196F3); // Plava boja kada je aktivno
        } else {
            btnLoad.setBackgroundColor(0xFFBDBDBD); // Siva boja kada je onemogućeno
        }
    }

    private void openFolderPicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Izaberi Vcam target folder"), requestCode);
        } catch (Exception e) {
            Toast.makeText(this, "Greška pri pokretanju birača: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                    // Prikazujemo preview sličicu izabranog videa
                    showVideoPreview(uri);
                    // Vršimo kopiranje u virtual.mp4 preko roota
                    processAndReplaceVcamFile(uri);
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void showVideoPreview(Uri sourceUri) {
        try {
            String sourceFilePath = getRealFilePathFromUri(sourceUri);
            if (sourceFilePath != null && new File(sourceFilePath).exists()) {
                // Generišemo mini-sličicu (Thumbnail) direktno iz video fajla
                Bitmap thumbnail = ThumbnailUtils.createVideoThumbnail(sourceFilePath, MediaStore.Images.Thumbnails.MINI_KIND);
                if (thumbnail != null) {
                    ivVideoPreview.setImageBitmap(thumbnail);
                    ivVideoPreview.setVisibility(View.VISIBLE);
                } else {
                    ivVideoPreview.setVisibility(View.GONE);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            ivVideoPreview.setVisibility(View.GONE);
        }
    }

    private void processAndReplaceVcamFile(Uri sourceUri) {
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

            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                executeRootCommand("mkdir -p '" + targetDir.getAbsolutePath() + "'");
            }

            File destinationFile = new File(targetDir, "virtual.mp4");

            // Kopiranje fajla pod imenom virtual.mp4 preko root privilegija
            String command = "cp -f '" + sourceFilePath + "' '" + destinationFile.getAbsolutePath() + "'";
            boolean success = executeRootCommand(command);

            if (success && destinationFile.exists()) {
                executeRootCommand("chmod 666 '" + destinationFile.getAbsolutePath() + "'");
                Toast.makeText(this, "Uspešno kreiran virtual.mp4 u target folderu!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Greška: Root kopiranje fajla nije uspelo.", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
