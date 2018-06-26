package com.burlap.filetransfer;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import com.facebook.react.bridge.*;
import com.facebook.react.bridge.Callback;
import okhttp3.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


public class FileTransferModule extends ReactContextBaseJavaModule {

    private final OkHttpClient client = new OkHttpClient();
    private static Map<String, String> header = new HashMap<>();
    private static Headers headerbuild;

    private String TAG = "ImageUploadAndroid";

    public FileTransferModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        // match up with the IOS name
        return "FileTransfer";
    }

    @ReactMethod
    public void upload(ReadableMap options, Callback complete) {

        final Callback completeCallback = complete;

        try {
            MultipartBody.Builder mRequestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM);

            ReadableArray files = options.getArray("files");
            String url = options.getString("url");
            String method = options.getString("method");

            if (options.hasKey("params")) {
                ReadableMap data = options.getMap("params");
                ReadableMapKeySetIterator iterator = data.keySetIterator();

                while (iterator.hasNextKey()) {
                    String key = iterator.nextKey();
                    if (ReadableType.String.equals(data.getType(key))) {
                        mRequestBody.addFormDataPart(key, data.getString(key));
                    }
                }
            }

            if (options.hasKey("headers")) {
                ReadableMap data = options.getMap("headers");
                ReadableMapKeySetIterator iterator = data.keySetIterator();

                while (iterator.hasNextKey()) {
                    String key = iterator.nextKey();
                    if (ReadableType.String.equals(data.getType(key))) {
                        header.put(key, data.getString(key));
                    }
                }
                headerbuild = Headers.of(header);
            } else {
                header.put("Accept", "application/json");
                headerbuild = Headers.of(header);
            }


            if (files.size() != 0) {
                for (int fileIndex = 0; fileIndex < files.size(); fileIndex++) {
                    ReadableMap file = files.getMap(fileIndex);
                    String uri = file.getString("filepath");
                    String mimeType = "image/png";
                    if (file.hasKey("filetype")) {
                        mimeType = file.getString("filetype");
                    }
                    Uri file_uri;
                    Context context = getReactApplicationContext();
                    String temp_file_uri = thisGetPath(context, Uri.parse(uri));
                    try {
                        if (temp_file_uri.indexOf("raw:") > -1) {
                            file_uri = Uri.parse(temp_file_uri.replaceFirst("raw:", ""));
                        }else{
                            file_uri = Uri.parse(temp_file_uri);
                        }
                    }catch (Exception e){
                        file_uri = Uri.parse(temp_file_uri);
                    }
                    File imageFile = new File(file_uri.getPath());
                    if (imageFile == null) {
                        Log.d(TAG, "FILE NOT FOUND");
                        completeCallback.invoke("FILE NOT FOUND", null);
                        return;
                    }


                    MediaType mediaType = MediaType.parse(mimeType);
                    String fileName = file.getString("filename");
                    String name = fileName;
                    if (file.hasKey("name")) {
                        name = file.getString("name");
                    }


                    mRequestBody.addFormDataPart(name, fileName, RequestBody.create(mediaType, imageFile));
                }
            }

            MultipartBody requestBody = mRequestBody.build();
            Request request;

            if (method.equals("PUT")) {
                request = new Request.Builder()
                        .headers(headerbuild)
                        .url(url)
                        .put(requestBody)
                        .build();
            } else {
                request = new Request.Builder()
                        .headers(headerbuild)
                        .url(url)
                        .post(requestBody)
                        .build();
            }

            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                Log.d(TAG, "Unexpected code" + response);
                completeCallback.invoke(response, null);
                return;
            }

            completeCallback.invoke(null, response.body().string());
        } catch (Exception e) {
            //Log.d(TAG, e.toString());
            completeCallback.invoke(e.toString(), null);
            return;
        }
    }

    public static String thisGetPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // Return the remote address
            try {
                if (isGooglePhotosUri(uri))
                    return uri.getLastPathSegment();
            }catch (Exception e){

            }
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }
}
