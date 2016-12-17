package alejandro.alvarado.com.streetwear;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    static final int REQUEST_IMAGE_CAPTURE = 1;
    private ImageView mImageView;
    private Uri mImageURI;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = (ImageView) findViewById(R.id.imageDisplay);
    }

    public void takePicture (View view) {
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            dispatchPictureIntent();
        }
    }

    private void dispatchPictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the file we want to store the image
            File imageFile = null;
            try {
                imageFile = createImageFile();
            } catch (IOException e) {
                // TODO: 11/4/16 Log the exception that occurrs here.
                Log.d("dispatchPictureIntent", e.getMessage().toString(), e);
            }
            if (imageFile != null) {
                mImageURI = FileProvider.getUriForFile(this,
                        "alejandro.alvarado.com.streetwear.fileprovider",
                        imageFile
                );
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mImageURI);
                takePictureIntent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private File createImageFile() throws IOException {
        // Create the image file with a unique name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String mImageFileName = "IMAGE_" + timeStamp + "_";
        // Get the directory to our file provider.
        // The information for this provider is given in the manifest as <provider>
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                mImageFileName,
                null,
                storageDir
        );
        // Delete the file once the virtual machine is stopped
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {

            try {
                AssetFileDescriptor assetFileDescriptor =
                        getContentResolver().openAssetFileDescriptor(mImageURI, "r");
                FileDescriptor fileDescriptor = assetFileDescriptor.getFileDescriptor();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                BitmapFactory.decodeFileDescriptor(fileDescriptor).
                        compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
                byte[] imageBytes = byteArrayOutputStream.toByteArray();
                assetFileDescriptor.close();
                this.getContentResolver().delete(mImageURI, null, null);
                new UploadImage().execute(imageBytes);
            } catch (FileNotFoundException e) {
                Log.d("OnActivityResult", "File was not able to be found.");
            } catch (IOException e) {
                Log.d("OnActivityResult", "IO Exception thrown.");
            }
        }
    }

    // Async class that will allow us to upload a file to our server
    private class UploadImage extends AsyncTask<byte[], Void, String> {

        private final String output_url = "https://streetanalyzer.herokuapp.com/file";
        private final String attachmentName = "bitmap";
        private final String attachmentFileName = "bitmap.bmp";
        private final String crlf = "\r\n";
        private final String twoHyphens = "--";
        private final String boundary = "*****";

        @Override
        protected String doInBackground(byte[]... params) {
            try {
                return uploadImage(params[0]);
            } catch (IOException e) {
                return "Unable to upload image.";
            }
        }

        private String uploadImage(byte[] imageBytes) throws IOException {

            URL url = new URL(output_url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setDoOutput(true);
                conn.setChunkedStreamingMode(0);
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("Cache-Control", "no-cache");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" +
                    this.boundary);

                DataOutputStream out = new DataOutputStream(conn.getOutputStream());
                writeStream(out, imageBytes);

                InputStream in = new BufferedInputStream(conn.getInputStream());
                readStream(in);
            } finally {
                conn.disconnect();
            }
            return null;
        }

        private void writeStream(DataOutputStream out, byte[] imageBytes) {

            try {
                out.writeBytes(this.twoHyphens + this.boundary + this.crlf);
                out.writeBytes("Content-Disposition: form-data; name=\"" +
                    this.attachmentName + "\";filename=\"" +
                    this.attachmentFileName + "\"" + this.crlf);
                out.writeBytes(this.crlf);
                out.write(imageBytes);
                out.writeBytes(this.crlf);
                out.writeBytes(this.twoHyphens + this.boundary + this.twoHyphens +
                    this.crlf);
                out.flush();
                out.close();
            } catch (IOException e) {
                Log.d("writeStream", "IO Exception when writing to out stream.");
            }
        }
        
        private void readStream(InputStream in) {
            String line = "";
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                StringBuilder response = new StringBuilder(in.available());
                while ((line = reader.readLine()) != null) {
                    response.append(line).append('\n');
                }
                reader.close();
                line = response.toString();
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("readStream", "Read the line: " + line);

            }

        }
    }

}
