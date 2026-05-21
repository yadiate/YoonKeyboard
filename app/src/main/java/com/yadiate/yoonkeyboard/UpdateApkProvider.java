package com.yadiate.yoonkeyboard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public final class UpdateApkProvider extends ContentProvider {
    private static final String APK_MIME = "application/vnd.android.package-archive";

    public static Uri uriFor(Context context, File apkFile) {
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".updateapk")
                .appendPath(apkFile.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return APK_MIME;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Read-only provider");
        }
        File file = fileForUri(uri);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file;
        try {
            file = fileForUri(uri);
        } catch (FileNotFoundException ex) {
            return null;
        }
        MatrixCursor cursor = new MatrixCursor(new String[]{
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE
        });
        cursor.addRow(new Object[]{file.getName(), file.length()});
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Insert is not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Delete is not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Update is not supported");
    }

    private File fileForUri(Uri uri) throws FileNotFoundException {
        Context context = getContext();
        if (context == null) {
            throw new FileNotFoundException("Provider context is unavailable");
        }
        String fileName = uri.getLastPathSegment();
        if (fileName == null || fileName.contains("/") || fileName.contains("\\")) {
            throw new FileNotFoundException("Invalid update APK path");
        }

        try {
            File updateDir = new File(context.getCacheDir(), "updates").getCanonicalFile();
            File file = new File(updateDir, fileName).getCanonicalFile();
            String updateDirPath = updateDir.getPath() + File.separator;
            if (!file.getPath().startsWith(updateDirPath) || !file.isFile()) {
                throw new FileNotFoundException("Update APK not found");
            }
            return file;
        } catch (IOException ex) {
            FileNotFoundException wrapped = new FileNotFoundException("Cannot resolve update APK");
            wrapped.initCause(ex);
            throw wrapped;
        }
    }
}
