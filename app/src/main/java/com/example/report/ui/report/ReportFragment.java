package com.example.report.ui.report;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.report.data.DatabaseHelper;
import com.example.report.databinding.FragmentReportBinding;
import com.example.report.utils.LocationHelper;
import com.example.report.utils.NotificationHelper;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportFragment extends Fragment implements OnMapReadyCallback {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    
    private FragmentReportBinding binding;
    private DatabaseHelper dbHelper;
    private LocationHelper locationHelper;
    private NotificationHelper notificationHelper;
    private GoogleMap googleMap;
    private Bitmap photoBitmap;
    private Location currentLocation;

    private long editingReportId = -1;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentReportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dbHelper = new DatabaseHelper(requireContext());
        locationHelper = new LocationHelper(requireContext());
        notificationHelper = new NotificationHelper(requireContext());

        // Set location update listener
        locationHelper.setLocationUpdateListener(new LocationHelper.OnLocationUpdateListener() {
            @Override
            public void onLocationUpdate(Location location) {
                currentLocation = location;
            }

            @Override
            public void onLocationError(String error) {
                Toast.makeText(requireContext(), "Erro ao obter localização: " + error, Toast.LENGTH_SHORT).show();
            }
        });

        if (getArguments() != null && getArguments().containsKey("reportId")) {
            editingReportId = getArguments().getLong("reportId");
            loadReportForEditing(editingReportId);
        }

        // Start location updates only if creating a new report
        if (editingReportId == -1) {
            locationHelper.startLocationUpdates();
        }

        setupMap();
        setupCategoryDropdown();
        setupClickListeners();
    }

    private void setupMap() {
        // Disable map setup to prevent location editing in report editing UI
        // SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
        //         .findFragmentById(com.example.report.R.id.locationMap);
        // if (mapFragment != null) {
        //     mapFragment.getMapAsync(this);
        // }
    }

    private void setupCategoryDropdown() {
        List<String> categories = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
            DatabaseHelper.TABLE_CATEGORIES,
            new String[]{DatabaseHelper.COLUMN_NAME},
            null,
            null,
            null,
            null,
            null
        );

        while (cursor.moveToNext()) {
            categories.add(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NAME)));
        }
        cursor.close();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            categories
        );

        ((AutoCompleteTextView) binding.categoryInput).setAdapter(adapter);
    }

    private void setupClickListeners() {
        binding.takePhotoButton.setOnClickListener(v -> dispatchTakePictureIntent());
        binding.submitButton.setOnClickListener(v -> submitReport());
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            if (extras != null) {
                photoBitmap = (Bitmap) extras.get("data");
                binding.photoPreview.setImageBitmap(photoBitmap);
            }
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        // Disable map interaction to prevent location editing
        // No implementation
    }

    private void submitReport() {
        String category = binding.categoryInput.getText().toString();
        String description = binding.descriptionInput.getText().toString();

        if (category.isEmpty() || description.isEmpty()) {
            Toast.makeText(requireContext(), "Preencha todos os campos", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentLocation == null) {
            Toast.makeText(requireContext(), "Aguarde a localização ser obtida", Toast.LENGTH_SHORT).show();
            return;
        }

       
        long categoryId = getCategoryId(category);
        if (categoryId == -1) {
            Toast.makeText(requireContext(), "Categoria inválida", Toast.LENGTH_SHORT).show();
            return;
        }

       
        byte[] photoData = null;
        if (photoBitmap != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            photoBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            photoData = stream.toByteArray();
        }

        
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_CATEGORY_ID, categoryId);
        values.put(DatabaseHelper.COLUMN_USER_ID, 1); 
        values.put(DatabaseHelper.COLUMN_DESCRIPTION, description);
        values.put(DatabaseHelper.COLUMN_PHOTO, photoData);
        values.put(DatabaseHelper.COLUMN_LATITUDE, currentLocation.getLatitude());
        values.put(DatabaseHelper.COLUMN_LONGITUDE, currentLocation.getLongitude());
        values.put(DatabaseHelper.COLUMN_DATETIME, timestamp);

        if (editingReportId == -1) {
           
            values.put(DatabaseHelper.COLUMN_STATUS, "Pendente");
            long newRowId = db.insert(DatabaseHelper.TABLE_PROBLEMS, null, values);

            if (newRowId != -1) {
                notificationHelper.showReportCreatedNotification(
                    "Problema Reportado",
                    "Seu reporte foi enviado com sucesso!"
                );
                clearForm();
                Toast.makeText(requireContext(), "Reporte enviado com sucesso", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Erro ao enviar reporte", Toast.LENGTH_SHORT).show();
            }
        } else {
          
            String selection = DatabaseHelper.COLUMN_ID + " = ?";
            String[] selectionArgs = {String.valueOf(editingReportId)};
            values.put(DatabaseHelper.COLUMN_STATUS, "Pendente"); 
            int count = db.update(DatabaseHelper.TABLE_PROBLEMS, values, selection, selectionArgs);

            if (count > 0) {
                Toast.makeText(requireContext(), "Reporte atualizado com sucesso", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Erro ao atualizar reporte", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private long getCategoryId(String categoryName) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String[] projection = {DatabaseHelper.COLUMN_ID};
        String selection = DatabaseHelper.COLUMN_NAME + " = ?";
        String[] selectionArgs = {categoryName};

        Cursor cursor = db.query(
            DatabaseHelper.TABLE_CATEGORIES,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            null
        );

        long categoryId = -1;
        if (cursor.moveToFirst()) {
            categoryId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID));
        }
        cursor.close();

        return categoryId;
    }

    private void clearForm() {
        binding.categoryInput.setText("");
        binding.descriptionInput.setText("");
        binding.photoPreview.setImageResource(android.R.drawable.ic_menu_camera);
        photoBitmap = null;
        editingReportId = -1;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        // Stop location updates to avoid memory leaks
        if (locationHelper != null) {
            locationHelper.stopLocationUpdates();
        }
    }

    private void loadReportForEditing(long reportId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String[] projection = {
            DatabaseHelper.COLUMN_ID,
            DatabaseHelper.COLUMN_CATEGORY_ID,
            DatabaseHelper.COLUMN_DESCRIPTION,
            DatabaseHelper.COLUMN_PHOTO,
            DatabaseHelper.COLUMN_LATITUDE,
            DatabaseHelper.COLUMN_LONGITUDE,
            DatabaseHelper.COLUMN_DATETIME,
            DatabaseHelper.COLUMN_STATUS
        };
        String selection = DatabaseHelper.COLUMN_ID + " = ?";
        String[] selectionArgs = {String.valueOf(reportId)};

        try (Cursor cursor = db.query(DatabaseHelper.TABLE_PROBLEMS, projection, selection, selectionArgs, null, null, null)) {
            if (cursor.moveToFirst()) {
                long categoryId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CATEGORY_ID));
                String description = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DESCRIPTION));
                byte[] photo = cursor.getBlob(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PHOTO));
                double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LATITUDE));
                double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LONGITUDE));
                String datetime = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DATETIME));
                String status = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_STATUS));

               
                String categoryName = getCategoryName(categoryId);

        
                binding.categoryInput.setText(categoryName);
                binding.descriptionInput.setText(description);
                if (photo != null) {
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(photo, 0, photo.length);
                    binding.photoPreview.setImageBitmap(bitmap);
                    photoBitmap = bitmap;
                }
             
                currentLocation = null;
            }
        }
    }

    private String getCategoryName(long categoryId) {
        String categoryName = "";
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String[] projection = {DatabaseHelper.COLUMN_NAME};
        String selection = DatabaseHelper.COLUMN_ID + " = ?";
        String[] selectionArgs = {String.valueOf(categoryId)};

        try (Cursor cursor = db.query(DatabaseHelper.TABLE_CATEGORIES, projection, selection, selectionArgs, null, null, null)) {
            if (cursor.moveToFirst()) {
                categoryName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NAME));
            }
        }
        return categoryName;
    }
}
