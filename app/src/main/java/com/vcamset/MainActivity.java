package com.vcamset;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_SELECT_MP4 = 1001;
    private static final int REQUEST_CODE_SELECT_SOURCE_DIR = 1002;
    private static final int REQUEST_CODE_SELECT_TARGET_DIR = 1003;

    private static final String PREFS_NAME = "VCamSetPrefs";
    private static final String KEY_SOURCE_PATH = "source_path";
    private static final String KEY_TARGET_PATH = "target_path";

    private Button btnLoad, btnSetSource, btnSetTarget;
    private TextView tvSourcePath, tvTargetPath;

    private String sourcePath = "";
    private String targetPath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnLoad = (Button) findViewById(R.id.btnLoad);
        btnSetSource = (Button) findViewById(R.id.btnSetSource);
        btnSetTarget = (Button) findViewById(R.id.btnSetTarget);
        tvSourcePath = (TextView) findViewById(R.id.tvSourcePath);
        tvTargetPath = (TextView) findViewById(R.id.tvTargetPath);

        loadSavedPaths();
        updateUIState();

        // Dugme "Load" otvara fajl menadžer u definisanom Source folderu ako je moguće
        btnLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMp4Picker();
            }
        });

        // Dugme za postavljanje izvornog foldera
        btnSetSource.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFolderPicker(REQUEST_CODE_SELECT_SOURCE_DIR);
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

    private void loadSavedPaths() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sourcePath = prefs.getString(KEY_SOURCE_PATH, "");
        targetPath = prefs.getString(KEY_TARGET_PATH, "");

        if (!sourcePath.isEmpty()) {
            tvSourcePath.setText("Source path: " + sourcePath);
        }
        if (!targetPath.isEmpty()) {
            tvTargetPath.setText("Target path: " + targetPath);
        }
    }

    private void savePaths() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_SOURCE_PATH, sourcePath);
        editor.putString(KEY_TARGET_PATH, targetPath);
        editor.commit();
    }

    private void updateUIState() {
        boolean isReady = !sourcePath.isEmpty() && !targetPath.isEmpty();
        btnLoad.setEnabled(isReady);
        if (isReady) {
            btnLoad.setBackgroundColor(0xFF2196F3); // Plava
        } else {
            btnLoad.setBackgroundColor(0xFFBDBDBD); // Siva kad je onemogućeno
        }
    }

    private void openFolderPicker(int requestCode) {
        // Na Androidu 7.1 koristimo standardni file/folder intent ili opšti get content
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Izaberi folder / lokaciju"), requestCode);
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
                if (requestCode == REQUEST_CODE_SELECT_SOURCE_DIR) {
                    // Ekstraktujemo folder putanju iz URI-ja ili koristimo realni fajl path ako je dostupan
                    String path = getPathFromUri(uri);
                    if (path != null) {
                        File f = new File(path);
                        sourcePath = f.isDirectory() ? f.getAbsolutePath() : f.getParent();
                    } else {
                        sourcePath = uri.getPath();
                    }
                    tvSourcePath.setText("Source path: " + sourcePath);
                    savePaths();
                    updateUIState();
                } else if (requestCode == REQUEST_CODE_SELECT_TARGET_DIR) {
                    String path = getPathFromUri(uri);
                    if (path != null) {
                        File f = new File(path);
                        targetPath = f.isDirectory() ? f.getAbsolutePath() : f.getParent();
                    } else {
                        targetPath = uri.getPath();
                    }
                    tvTargetPath.setText("Target path: " + targetPath);
                    savePaths();
                    updateUIState();
                } else if (requestCode == REQUEST_CODE_SELECT_MP4) {
                    processAndReplaceVcamFile(uri);
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void processAndReplaceVcamFile(Uri sourceUri) {
        try {
            if (targetPath.isEmpty()) {
                Toast.makeText(this, "Target folder nije podešen!", Toast.LENGTH_SHORT).show();
                return;
            }

            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            File destinationFile = new File(targetDir, "virtual.mp4");

            InputStream inputStream = getContentResolver().openInputStream(sourceUri);
            OutputStream outputStream = new FileOutputStream(destinationFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.flush();
            outputStream.close();
            inputStream.close();

            Toast.makeText(this, "Uspešno zamenjen virtual.mp4!", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Greška pri kopiranju fajla: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getPathFromUri(Uri uri) {
        // Jednostavna dekodizacija standardnih fajl putanja na starijim Android sistemima
        String path = uri.getPath();
        if (path != null && path.contains(":")) {
            // Primer: /document/primary:Download/video.mp4 -> /storage/emulated/0/Download/video.mp4
            String[] parts = path.split(":");
            if (parts.length > 1 && parts[0].contains("primary")) {
                return android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + parts[1];
            }
        }
        return path;
    }
}