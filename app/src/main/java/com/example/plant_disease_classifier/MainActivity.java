package com.example.plant_disease_classifier;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.FileInputStream;
import java.nio.channels.FileChannel;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private static final String LOGTAG = "PlantDiseaseClassifier";
    public static final int cam_code = 201;
    public static final int code_request = 202;
    public static final int gallery_req_code = 106;

    private static final int mCameraRequestCode = 0;
    private static final int mGalleryRequestCode = 2;

    ImageView ImageSelected;
    Button cam_btn, gallery_btn;
    TextView outputFromModel;
    Bitmap bit;
    Button detect_btn;
    Interpreter interpreter;
    String labelPath;
    Bitmap mbitmap;
    List<String> labels_req;


    //To load the model
    private MappedByteBuffer loadModelFile(String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageSelected = findViewById(R.id.displayImageView);
        cam_btn = findViewById(R.id.cam_btn);
        gallery_btn = findViewById(R.id.gallery_btn);
        detect_btn = findViewById(R.id.detect_btn);
        outputFromModel = findViewById(R.id.textView2);
        int[] resiseDims = new int[]{1, 224, 224, 3};

        try {
            interpreter = new Interpreter(loadModelFile("plant_disease_model_assests.tflite"));
            interpreter.resizeInput(0, resiseDims);
            labelPath = "plant_labels_assests.txt";
            labels_req = FileUtil.loadLabels(this, labelPath);
        } catch (IOException e) {
            Log.e(LOGTAG, "Error reading label file", e);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
//        labelPath = "plant_labels_assests.txt";
//        try {
//            labels_req = FileUtil.loadLabels(this, labelPath);
//        } catch (IOException e) {
//            Log.e("tfliteSupport", "Error reading label file", e);
//        }

        cam_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, "cam is clicked", Toast.LENGTH_SHORT).show();
                takeCamPermission();
            }

        });

        gallery_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent gallery_intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(gallery_intent, mGalleryRequestCode);
            }
        });


        detect_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Filter is true here as it enhances image quality by using bilinear transformation
                mbitmap = Bitmap.createScaledBitmap(mbitmap, 224, 224, true);
                ByteBuffer byteBuffer = convertBitmapToByteBuffer(mbitmap);
                FloatBuffer outBuf = FloatBuffer.allocate(39);
                interpreter.run(byteBuffer, outBuf);
                Log.d(LOGTAG, Arrays.toString(outBuf.array()));
                float[] outArray = outBuf.array();
                float highest = outArray[0];
                int maxIdx = 0;

                for (int idx = 1; idx < outArray.length; idx++) {
                    if (outArray[idx] > highest) {
                        highest = outArray[idx];
                        maxIdx = idx;
                    }
                }
                outputFromModel.clearComposingText();
                outputFromModel.setText(labels_req.get(maxIdx));
//                interpreter.close();
            }
        });

    }

    private void takeCamPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, cam_code);
        } else {
            Intent CameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(CameraIntent, mCameraRequestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == cam_code) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent CameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(CameraIntent, mCameraRequestCode);
            } else {
                Toast.makeText(this, "Camera Permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //To store the captured image in image view
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == mCameraRequestCode) {
            if (resultCode == Activity.RESULT_OK & (data != null)) {
                mbitmap = (Bitmap) data.getExtras().get("data");
                mbitmap = scaleImage(mbitmap);
                ImageSelected.setImageBitmap(mbitmap);
            }
        }
        if (requestCode == mGalleryRequestCode) {
            if (data != null) {
                Uri contentUri = data.getData();
                try {
                    mbitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), contentUri);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mbitmap = scaleImage(mbitmap);
                ImageSelected.setImageBitmap(mbitmap);
            }
        }
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[224 * 224];

        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < 224; i++) {
            for (int j = 0; j < 224; j++) {
                int val = intValues[pixel++];

                byteBuffer.putFloat((((val >> 16) & 0xFF)) / 255.0f);
                byteBuffer.putFloat((((val >> 8) & 0xFF)) / 255.0f);
                byteBuffer.putFloat((((val) & 0xFF)) / 255.0f);
            }
        }
        return byteBuffer;
    }

    public Bitmap scaleImage(Bitmap bitmap) {
        int orginalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();

        float finalWidth = 224.0f / orginalWidth;
        float finalHeight = 224.0f / originalHeight;
        Matrix matrix = new Matrix();
        matrix.postScale(finalWidth, finalHeight);
        return Bitmap.createBitmap(bitmap, 0, 0, orginalWidth, originalHeight, matrix, true);
    }
}