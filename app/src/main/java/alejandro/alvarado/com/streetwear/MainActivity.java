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
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
public class MainActivity extends AppCompatActivity {

    private static final String FILE_NAME = "StreetWear";
    static final int REQUEST_IMAGE_CAPTURE = 1;
    private ImageView mImageView;
    private File mImageFile = null;
    private Uri mImageUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = (ImageView) findViewById(R.id.imageDisplay);
    }

    // these activity lifeycle method were created only for testing
    @Override
    protected void onPause() {
        super.onPause();
        Log.d("onPause","paused");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d("onRestart", "restarted");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("onResume", "resumed");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("onStop", "stopped");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("onDestroy", "destroyed");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("onStart", "started");
    }

    /**
     * Checks if there is a connection to the internet using wifi or data.
     * If there is, opens the camera app
     * @param view
     */

    public void takePicture (View view) {
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            // The phone is online, we can safely try to take a picture
            // and upload to the server
            dispatchPictureIntent();
        }
    }

    /**
     * Calls the android phone's camera app to take a picture and save the image in
     * a specified location and stores the uri path for later use. The response of the
     * camera intent is passed to onActivityResult
     */
    private void dispatchPictureIntent() {
        // Create the intent to take an image
        // We will use the existing application from the android phone
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Checks if the phone is capable of handling this intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the file we want to store the image in...
            try {
                mImageUri = Uri.fromFile(createImageFile());
                if (mImageUri != null) {

                    // Tell the photo application to store the image at that location
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);
                    // Give the photo application control to take the picture
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                }
            } catch (IOException e) {
                Log.d("dispatchPictureIntent", e.getMessage(), e);
            }
        }
    }

    /**
     * Creates a file containing its path in the directory where the picture will be saved
     *
     * @return file of the picture
     * @throws IOException
     */

    private File createImageFile() throws IOException {
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mImageFile = new File(path, FILE_NAME);

        // Get the directory to our file provider.
        return mImageFile;
    }

    /**
     * Displays the picture as a thumbnail
     */
    private void showThumbnail(){
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        Bitmap imageBitmap = BitmapFactory.decodeFile(mImageFile.getAbsolutePath(), bmOptions);
        mImageView.setImageBitmap(imageBitmap);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // The image application has returned and if everything is ok, proceed to reading
        // image from URI
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            if (mImageUri != null) {
                showThumbnail();
                try {
                    // Open the file descriptor from the URI we had saved before
                    AssetFileDescriptor assetFileDescriptor =
                            getContentResolver().openAssetFileDescriptor(mImageUri, "r");
                    FileDescriptor fileDescriptor = assetFileDescriptor.getFileDescriptor();
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    // Convert the image at that location into a JPEG file
                    BitmapFactory.decodeFileDescriptor(fileDescriptor).
                            compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
                    // Save that image into a byte array
                    byte[] imageBytes = byteArrayOutputStream.toByteArray();
                    assetFileDescriptor.close();
                    // Try to delete the image from the phone
                    // TODO: (COMPLETED) 12/17/16 Image is not being deleted properly
                    mImageFile.delete();
                    // this.getContentResolver().delete(mImageUri, null, null);
                    // Upload image to the server
                    new UploadImage().execute(imageBytes);
                    Toast.makeText(this, "Image uploaded successfully", Toast.LENGTH_LONG).show();
                } catch (FileNotFoundException e) {
                    Log.d("OnActivityResult", "Image file was not able to be found.");
                } catch (NullPointerException e) {
                    Log.d("OnActivityResult", "Image file was not able to be found");
                } catch (IOException e) {
                    Log.d("OnActivityResult", "IO Exception thrown.");
                }
            }
        }
    }

    // Async class that will allow us to upload a file to our server
    private class UploadImage extends AsyncTask<byte[], Void, String> {

        // Used to prepare the image for upload
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

            // Open a connection to the StreetWear Server
            URL url = new URL(output_url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                // Apply some settings so that we can execute our POST request
                conn.setDoOutput(true);
                conn.setChunkedStreamingMode(0);
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("Cache-Control", "no-cache");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" +
                        this.boundary);

                // Send the message to the server
                DataOutputStream out = new DataOutputStream(conn.getOutputStream());
                writeStream(out, imageBytes);

                // Get the response from the server
                InputStream in = new BufferedInputStream(conn.getInputStream());
                readStream(in);
            } finally {
                conn.disconnect();
            }
            return null;
        }

        private void writeStream(DataOutputStream out, byte[] imageBytes) {

            try {
                // Prepare the message to the server
                // Must be formatted to the POST standard
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
                // Gather the response from the server
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                StringBuilder response = new StringBuilder(in.available());
                while ((line = reader.readLine()) != null) {
                    response.append(line).append('\n');
                }
                reader.close();
                // Do something with the response
                // TODO: 12/17/16 The response will tell us what the results of the analyse were
                // Need to map this result onto a map for client to view
                line = response.toString();
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("readStream", "Read the line: " + line);

            }

        }
    }

}
