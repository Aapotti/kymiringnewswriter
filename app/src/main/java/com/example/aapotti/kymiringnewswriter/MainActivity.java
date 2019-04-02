package com.example.aapotti.kymiringnewswriter;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;

import id.zelory.compressor.Compressor;

public class MainActivity extends AppCompatActivity {

    private EditText titleET, contentET;
    private ImageView addImageIW;

    private StorageReference storageReference;
    private DatabaseReference newsRef;

    private ProgressDialog loadingBar;

    private String imageDownloadUlr;
    private String saveCurrentDateForPosts, saveCurrentTimeForImage, saveCurrentTimeForPosts, saveCurrentDateForImage;
    private String newsTitle, newsContent;

    private Uri contentUri;

    final static int galleryPic = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        titleET = (EditText)findViewById(R.id.titleET);
        contentET = (EditText)findViewById(R.id.contentET);
        addImageIW = (ImageView)findViewById(R.id.addImageIW);

        storageReference = FirebaseStorage.getInstance().getReference().child("newsImages");
        newsRef = FirebaseDatabase.getInstance().getReference().child("News");

        loadingBar = new ProgressDialog(this);
    }

    public void addImage(View v)
    {
        //ASKING PERMISSION FOR PHOTO GALLERY
        if(checkCallingOrSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
        else
        {
            Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(galleryIntent, galleryPic);
        }
    }

    //CHECK IF USER GAVE ACCES TO PHOTOS GALLERY
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == 1)
        {
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(galleryIntent, galleryPic);
            }
        }
    }

    //REDUCE IMAGE SIZE AND SEND IT TO CROPPING
    File compressedImageFile;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == galleryPic && resultCode == RESULT_OK && data != null)
        {
            Uri imageUri = data.getData();
            File f = new File(getRealPathFromURI(imageUri));
            try
            {
                compressedImageFile = new Compressor(this).compressToFile(f);
            }
            catch (Exception e)
            {
                String message = e.getMessage();
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
            contentUri = Uri.fromFile(compressedImageFile);

            addImageIW.setImageURI(null);
            addImageIW.setImageURI(contentUri);
        }
    }

    //GET FILE PATH FROM URI
    private String getRealPathFromURI(Uri contentURI) {
        String result;
        Cursor cursor = getContentResolver().query(contentURI, null, null, null, null);
        if (cursor == null) { // Source is Dropbox or other similar local file path
            result = contentURI.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    private String getRandomNameForImage()
    {
        Calendar calendarImageNameDate = Calendar.getInstance();
        SimpleDateFormat currentDate = new SimpleDateFormat("dd-MMMM-yyyy");
        saveCurrentDateForImage = currentDate.format(calendarImageNameDate.getTime());

        Calendar calendarPreciseTime = Calendar.getInstance();
        SimpleDateFormat currentTimePrecise = new SimpleDateFormat("HH:mm:ss:SSS");
        saveCurrentTimeForImage = currentTimePrecise.format(calendarPreciseTime.getTime());

        String imageName = "newsImage_" + saveCurrentTimeForImage + "_" + saveCurrentDateForImage;

        return imageName;
    }

    public void publish(View v)
    {
        Calendar calendarDate = Calendar.getInstance();
        SimpleDateFormat currentDate = new SimpleDateFormat("dd-MMMM-yyyy");
        saveCurrentDateForPosts = currentDate.format(calendarDate.getTime());

        Calendar calendarTime = Calendar.getInstance();
        SimpleDateFormat currentTimeForPosts = new SimpleDateFormat("HH:mm");
        saveCurrentTimeForPosts = currentTimeForPosts.format(calendarTime.getTime());

        if(TextUtils.isEmpty(newsTitle) && TextUtils.isEmpty(newsContent))
        {
            newsTitle = titleET.getText().toString();
            newsContent = contentET.getText().toString();
        }

        if(TextUtils.isEmpty(newsTitle) || TextUtils.isEmpty(newsContent))
        {
            Toast.makeText(this, "Please provide title, content and image for the article", Toast.LENGTH_SHORT).show();
        }
        else
        {
            loadingBar.setTitle("Publishing article");
            loadingBar.setMessage("Please wait");
            loadingBar.setCanceledOnTouchOutside(true);
            loadingBar.show();

            StorageReference filePath = storageReference.child(getRandomNameForImage() + ".jpg");

            filePath.putFile(contentUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>()
            {
                @Override
                public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task)
                {
                    if(task.isSuccessful())
                    {
                        loadingBar.dismiss();
                        imageDownloadUlr = task.getResult().getDownloadUrl().toString();

                        HashMap newsMap = new HashMap();
                        newsMap.put("title", newsTitle);
                        newsMap.put("content", newsContent);
                        newsMap.put("date", saveCurrentTimeForPosts + " " + saveCurrentDateForPosts);
                        newsMap.put("image", imageDownloadUlr);

                        newsRef.push().updateChildren(newsMap);

                        Intent intent = getIntent();
                        finish();
                        startActivity(intent);
                    }
                    else
                    {
                        loadingBar.dismiss();
                        Toast.makeText(MainActivity.this, "Something went wrong, please try again later", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }
}
