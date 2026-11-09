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

import java.io.DataOutputStream;
import java.io.File;

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

        // Dugme "Load" otvara birač MP4 fajlova
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
            btnLoad.setBackgroundColor(0xFF2196F3); // Plava boja kad je aktivno
        } else {
            btnLoad.setBackgroundColor(0xFFBDBDBD); // Siva boja kad je onemogućeno
        }
    }

    private void openFolderPicker(int requestCode) {
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
                    String path = getRealFilePathFromUri(uri);
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
                    String path = getRealFilePathFromUri(uri);
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

            String sourceFilePath = getRealFilePathFromUri(sourceUri);
            if (sourceFilePath == null || !new File(sourceFilePath).exists()) {
                Toast.makeText(this, "Ne mogu da pronađem izvorni fajl!", Toast.LENGTH_SHORT).show();
                return;
            }

            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                executeRootCommand("mkdir -p '" + targetDir.getAbsolutePath() + "'");
            }

            File destinationFile = new File(targetDir, "virtual.mp4");

            // Izvršavanje root kopiranja sa -f (force)
            String command = "cp -f '" + sourceFilePath + "' '" + destinationFile.getAbsolutePath() + "'";
            boolean success = executeRootCommand(command);

            if (success && destinationFile.exists()) {
                executeRootCommand("chmod 666 '" + destinationFile.getAbsolutePath() + "'");
                Toast.makeText(this, "Uspešno zamenjen virtual.mp4 preko roota!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Greška: Root kopiranje nije uspelo.", Toast.LENGTH_LONG).show();
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
