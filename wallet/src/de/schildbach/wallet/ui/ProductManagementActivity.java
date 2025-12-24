/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.data.Category;
import de.schildbach.wallet.data.Product;
import de.schildbach.wallet.service.PointOfSaleWebService;
import de.schildbach.wallet.service.PointOfSaleBackgroundService;
import de.schildbach.wallet.Configuration;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Point of Sale Product Management Activity
 * 
 * Allows merchants to manage products with categories, images, prices, and quantities
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class ProductManagementActivity extends AbstractWalletActivity {
    private static final Logger log = LoggerFactory.getLogger(ProductManagementActivity.class);
    
    private static final int REQUEST_CODE_TAKE_PHOTO = 1001;
    private static final int REQUEST_CODE_PICK_IMAGE = 1002;
    
    private AddressBookDatabase database;
    private RecyclerView recyclerProducts;
    private Button btnAddProduct;
    private TextView textWebUrl;
    private Spinner spinnerCategoryFilter;
    private LinearLayout layoutCategoryFilter;
    private android.widget.Switch switchBackgroundService;
    private ProductsAdapter adapter;
    private List<Product> products = new ArrayList<>();
    private List<Product> allProducts = new ArrayList<>(); // Store all products for filtering
    private List<Category> categories = new ArrayList<>();
    private PointOfSaleWebService webService;
    private WalletApplication application;
    private Configuration config;
    private long selectedCategoryId = -1; // -1 means "All Categories"
    
    private String currentPhotoPath;
    private ImageView currentDialogImagePreview; // Reference to dialog's image preview
    private String[] dialogImagePathRef; // Reference to dialog's finalImagePath array for updating
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_management);
        
        application = getWalletApplication();
        config = application.getConfiguration();
        database = AddressBookDatabase.getDatabase(this);
        
        recyclerProducts = findViewById(R.id.recycler_products);
        btnAddProduct = findViewById(R.id.btn_add_product);
        textWebUrl = findViewById(R.id.text_web_url);
        spinnerCategoryFilter = findViewById(R.id.spinner_category_filter);
        layoutCategoryFilter = findViewById(R.id.layout_category_filter);
        switchBackgroundService = findViewById(R.id.switch_background_service);
        
        recyclerProducts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductsAdapter();
        recyclerProducts.setAdapter(adapter);
        
        btnAddProduct.setOnClickListener(v -> showAddProductDialog(null));
        
        // Setup category filter
        spinnerCategoryFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    selectedCategoryId = -1; // "All Categories"
                } else {
                    Category selectedCategory = categories.get(position - 1);
                    selectedCategoryId = selectedCategory.getId();
                }
                filterProducts();
            }
            
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                selectedCategoryId = -1;
                filterProducts();
            }
        });
        
        // Setup background service toggle
        boolean backgroundServiceEnabled = config.getPosBackgroundServiceEnabled();
        switchBackgroundService.setChecked(backgroundServiceEnabled);
        switchBackgroundService.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setPosBackgroundServiceEnabled(isChecked);
            if (isChecked) {
                // Start background service
                PointOfSaleBackgroundService.start(this);
                Toast.makeText(this, "Background service started - monitoring payments", Toast.LENGTH_SHORT).show();
            } else {
                // Stop background service
                PointOfSaleBackgroundService.stop(this);
                Toast.makeText(this, "Background service stopped", Toast.LENGTH_SHORT).show();
            }
        });
        
        // Start web service (always start for UI access)
        webService = new PointOfSaleWebService(application);
        webService.start();
        
        // Start background service if enabled
        if (backgroundServiceEnabled) {
            PointOfSaleBackgroundService.start(this);
        }
        
        // Display web URL
        updateWebUrl();
        
        loadProducts();
        loadCategories();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Only stop web service if background service is not enabled
        // If background service is enabled, it will keep the web service running
        if (webService != null && !config.getPosBackgroundServiceEnabled()) {
            webService.stop();
        }
    }
    
    private void updateWebUrl() {
        String ipAddress = getLocalIpAddress();
        if (ipAddress != null && textWebUrl != null) {
            String url = "http://" + ipAddress + ":" + webService.getPort();
            textWebUrl.setText("Access POS online: " + url);
            textWebUrl.setOnClickListener(v -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("POS URL", url);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "URL copied to clipboard", Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address instanceof java.net.Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting local IP address", e);
        }
        return null;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadProducts();
        loadCategories();
    }
    
    private void loadProducts() {
        try {
            allProducts = database.productDao().getAllProducts();
            filterProducts();
            updateCategoryFilter();
        } catch (Exception e) {
            log.error("Error loading products", e);
            Toast.makeText(this, "Error loading products", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void loadCategories() {
        try {
            categories = database.categoryDao().getAllCategories();
            updateCategoryFilter();
        } catch (Exception e) {
            log.error("Error loading categories", e);
        }
    }
    
    private void updateCategoryFilter() {
        if (categories != null && !categories.isEmpty()) {
            // Show filter
            layoutCategoryFilter.setVisibility(View.VISIBLE);
            
            // Setup spinner
            List<String> categoryNames = new ArrayList<>();
            categoryNames.add("All Categories");
            for (Category cat : categories) {
                categoryNames.add(cat.getName());
            }
            
            ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_item, categoryNames);
            categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCategoryFilter.setAdapter(categoryAdapter);
            
            // Reset to "All Categories" if selection is invalid
            if (selectedCategoryId != -1) {
                boolean found = false;
                for (int i = 0; i < categories.size(); i++) {
                    if (categories.get(i).getId() == selectedCategoryId) {
                        spinnerCategoryFilter.setSelection(i + 1);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    selectedCategoryId = -1;
                    spinnerCategoryFilter.setSelection(0);
                }
            }
        } else {
            // Hide filter if no categories
            layoutCategoryFilter.setVisibility(View.GONE);
            selectedCategoryId = -1;
        }
    }
    
    private void filterProducts() {
        if (selectedCategoryId == -1) {
            // Show all products
            products = new ArrayList<>(allProducts);
        } else {
            // Filter by selected category
            products = new ArrayList<>();
            for (Product product : allProducts) {
                if (product.getCategoryId() == selectedCategoryId) {
                    products.add(product);
                }
            }
        }
        adapter.notifyDataSetChanged();
        
        // Show/hide empty state
        View emptyState = findViewById(R.id.layout_empty_state);
        if (products.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
        } else {
            emptyState.setVisibility(View.GONE);
        }
    }
    
    private void showAddProductDialog(Product product) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_product, null);
        builder.setView(dialogView);
        
        Spinner spinnerCategory = dialogView.findViewById(R.id.spinner_category);
        EditText editCategoryNew = dialogView.findViewById(R.id.edit_category_new);
        Button btnAddCategory = dialogView.findViewById(R.id.btn_add_category);
        EditText editName = dialogView.findViewById(R.id.edit_name);
        EditText editDescription = dialogView.findViewById(R.id.edit_description);
        EditText editWeight = dialogView.findViewById(R.id.edit_weight);
        EditText editQuantity = dialogView.findViewById(R.id.edit_quantity);
        EditText editPrice = dialogView.findViewById(R.id.edit_price);
        ImageView imagePreview = dialogView.findViewById(R.id.image_preview);
        Button btnSelectImage = dialogView.findViewById(R.id.btn_select_image);
        Button btnTakePhoto = dialogView.findViewById(R.id.btn_take_photo);
        
        // Store reference to image preview for updating after photo selection
        currentDialogImagePreview = imagePreview;
        
        // Setup category spinner
        List<String> categoryNames = new ArrayList<>();
        categoryNames.add("Select existing category...");
        for (Category cat : categories) {
            categoryNames.add(cat.getName());
        }
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, categoryNames);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);
        
        String imagePath = null;
        long selectedCategoryId = -1;
        if (product != null) {
            editName.setText(product.getName());
            editDescription.setText(product.getDescription());
            if (product.getWeight() != null) {
                editWeight.setText(String.valueOf(product.getWeight()));
            }
            // Show quantity or empty if unlimited (-1)
            if (product.getQuantity() > 0) {
                editQuantity.setText(String.valueOf(product.getQuantity()));
            }
            // Convert from smallest unit to DOGE for display (1 DOGE = 100,000,000 smallest units)
            double priceInDoge = product.getPriceDoge() / 100000000.0;
            editPrice.setText(String.valueOf(priceInDoge));
            imagePath = product.getImagePath();
            selectedCategoryId = product.getCategoryId();
            
            // Set spinner to current category
            Category cat = database.categoryDao().getCategoryById(product.getCategoryId());
            if (cat != null) {
                int position = categoryNames.indexOf(cat.getName());
                if (position > 0) {
                    spinnerCategory.setSelection(position);
                }
            }
            
            if (imagePath != null && !imagePath.isEmpty()) {
                loadImagePreview(imagePreview, imagePath);
            } else {
                // Clear any previous image
                imagePreview.setImageBitmap(null);
                imagePreview.setBackgroundColor(getResources().getColor(R.color.bg_level2));
            }
        } else {
            // Clear image for new product
            imagePreview.setImageBitmap(null);
            imagePreview.setBackgroundColor(getResources().getColor(R.color.bg_level2));
        }
        
        final String[] finalImagePath = {imagePath};
        // Store reference so we can update it when image is selected
        dialogImagePathRef = finalImagePath;
        
        AlertDialog dialog = builder.create();
        
        // Toggle new category input
        btnAddCategory.setOnClickListener(v -> {
            if (editCategoryNew.getVisibility() == View.GONE) {
                editCategoryNew.setVisibility(View.VISIBLE);
                spinnerCategory.setSelection(0); // Reset to "Select existing..."
            } else {
                editCategoryNew.setVisibility(View.GONE);
                editCategoryNew.setText("");
            }
        });
        
        btnSelectImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
        });
        
        btnTakePhoto.setOnClickListener(v -> takePhoto());
        
        dialogView.findViewById(R.id.btn_save).setOnClickListener(v -> {
            String categoryName;
            // Get category name from spinner or new input
            if (editCategoryNew.getVisibility() == View.VISIBLE && !editCategoryNew.getText().toString().trim().isEmpty()) {
                categoryName = editCategoryNew.getText().toString().trim();
            } else {
                int selectedPos = spinnerCategory.getSelectedItemPosition();
                if (selectedPos <= 0) {
                    Toast.makeText(this, "Please select or enter a category", Toast.LENGTH_SHORT).show();
                    return;
                }
                categoryName = categoryNames.get(selectedPos);
            }
            
            String name = editName.getText().toString().trim();
            String description = editDescription.getText().toString().trim();
            String weightStr = editWeight.getText().toString().trim();
            String quantityStr = editQuantity.getText().toString().trim();
            String priceStr = editPrice.getText().toString().trim();
            
            if (name.isEmpty() || priceStr.isEmpty()) {
                Toast.makeText(this, "Please fill in name and price", Toast.LENGTH_SHORT).show();
                return;
            }
            
            try {
                // Get or create category
                Category category = database.categoryDao().getCategoryByName(categoryName);
                if (category == null) {
                    category = new Category(categoryName);
                    long categoryId = database.categoryDao().insertCategory(category);
                    category.setId(categoryId);
                }
                
                // Parse inputs
                Double weight = weightStr.isEmpty() ? null : Double.parseDouble(weightStr);
                // If quantity is empty, use -1 for unlimited
                int quantity = quantityStr.isEmpty() ? -1 : Integer.parseInt(quantityStr);
                // Parse price as double and convert to smallest unit (1 DOGE = 100,000,000 smallest units)
                double priceDouble = Double.parseDouble(priceStr);
                long priceDoge = Math.round(priceDouble * 100000000L); // Convert DOGE to smallest unit
                
                // Get the current image path (use currentPhotoPath if set, otherwise use saved path)
                String imagePathToSave = (currentPhotoPath != null && !currentPhotoPath.isEmpty()) 
                    ? currentPhotoPath 
                    : finalImagePath[0];
                
                log.info("Saving product with image path: {}", imagePathToSave);
                
                if (product == null) {
                    // Create new product
                    Product newProduct = new Product(category.getId(), name, description, weight, 
                                                     imagePathToSave, quantity, priceDoge);
                    long productId = database.productDao().insertProduct(newProduct);
                    log.info("Product created with ID: {}, image path: {}", productId, imagePathToSave);
                    Toast.makeText(this, "Product added", Toast.LENGTH_SHORT).show();
                } else {
                    // Update existing product
                    product.setCategoryId(category.getId());
                    product.setName(name);
                    product.setDescription(description);
                    product.setWeight(weight);
                    product.setImagePath(imagePathToSave);
                    product.setQuantity(quantity);
                    product.setPriceDoge(priceDoge);
                    product.setUpdatedTimestamp(System.currentTimeMillis());
                    database.productDao().updateProduct(product);
                    log.info("Product updated with image path: {}", imagePathToSave);
                    Toast.makeText(this, "Product updated", Toast.LENGTH_SHORT).show();
                }
                
                // Reset currentPhotoPath for next dialog
                currentPhotoPath = null;
                dialog.dismiss();
                loadProducts(); // This will reload and update filter
                loadCategories(); // This will reload and update filter
            } catch (Exception e) {
                log.error("Error saving product", e);
                Toast.makeText(this, "Error saving product: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            currentPhotoPath = null; // Reset
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    private void takePhoto() {
        try {
            // Check camera permission first
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                log.info("Camera permission not granted, requesting...");
                requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQUEST_CODE_TAKE_PHOTO);
                return;
            }
            log.info("Camera permission granted");
            
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) == null) {
                Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show();
                return;
            }
            
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                log.error("Error creating photo file", ex);
                Toast.makeText(this, "Error creating photo file: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            
            if (photoFile != null) {
                currentPhotoPath = photoFile.getAbsolutePath();
                log.info("Photo path set to: {}", currentPhotoPath);
                
                Uri photoURI = FileProvider.getUriForFile(this,
                        "org.dogecoin.wallet.file_attachment", photoFile);
                log.info("Photo URI: {}", photoURI);
                
                // Try with FileProvider
                try {
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    
                    log.info("Starting camera activity with FileProvider...");
                    startActivityForResult(takePictureIntent, REQUEST_CODE_TAKE_PHOTO);
                    log.info("Camera activity started successfully");
                } catch (Exception e) {
                    log.error("Error starting camera activity", e);
                    Toast.makeText(this, "Error starting camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else {
                log.error("Photo file is null");
                Toast.makeText(this, "Failed to create photo file", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            log.error("Error taking photo", e);
            Toast.makeText(this, "Error opening camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "POS_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        return image;
    }
    
    private void loadImagePreview(ImageView imageView, String imagePath) {
        try {
            if (imagePath == null || imagePath.isEmpty()) {
                imageView.setImageBitmap(null);
                imageView.setBackgroundColor(getResources().getColor(R.color.bg_level2));
                return;
            }
            
            File imgFile = new File(imagePath);
            if (imgFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                if (bitmap != null) {
                    // Resize for POS (max 800x800)
                    bitmap = resizeBitmapForPOS(bitmap, 800, 800);
                    imageView.setImageBitmap(bitmap);
                    imageView.setBackgroundColor(getResources().getColor(android.R.color.transparent));
                    imageView.setVisibility(View.VISIBLE);
                } else {
                    log.warn("Failed to decode image file: {}", imagePath);
                    imageView.setImageBitmap(null);
                    imageView.setBackgroundColor(getResources().getColor(R.color.bg_level2));
                }
            } else {
                log.warn("Image file does not exist: {}", imagePath);
                imageView.setImageBitmap(null);
                imageView.setBackgroundColor(getResources().getColor(R.color.bg_level2));
            }
        } catch (Exception e) {
            log.error("Error loading image preview", e);
            imageView.setImageBitmap(null);
            imageView.setBackgroundColor(getResources().getColor(R.color.bg_level2));
        }
    }
    
    private Bitmap resizeBitmapForPOS(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap;
        }
        
        float scale = Math.min((float) maxWidth / width, (float) maxHeight / height);
        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);
        
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        return resized;
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_CODE_TAKE_PHOTO) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Permission granted, retry taking photo
                takePhoto();
            } else {
                Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_TAKE_PHOTO && resultCode == RESULT_OK) {
            if (currentPhotoPath != null) {
                log.info("Photo taken, path: {}", currentPhotoPath);
                // Compress and resize image
                compressAndResizeImage(currentPhotoPath);
                // Update dialog image preview if available
                if (currentDialogImagePreview != null) {
                    loadImagePreview(currentDialogImagePreview, currentPhotoPath);
                    // Update finalImagePath for saving
                    if (dialogImagePathRef != null) {
                        dialogImagePathRef[0] = currentPhotoPath;
                        log.info("Updated dialog image path reference to: {}", currentPhotoPath);
                    }
                }
            } else {
                log.warn("Photo taken but currentPhotoPath is null");
            }
        } else if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            try {
                String imagePath = copyImageToInternalStorage(selectedImageUri);
                currentPhotoPath = imagePath;
                log.info("Image picked, path: {}", imagePath);
                // Compress and resize
                compressAndResizeImage(imagePath);
                // Update dialog image preview if available
                if (currentDialogImagePreview != null) {
                    loadImagePreview(currentDialogImagePreview, imagePath);
                    // Update finalImagePath for saving
                    if (dialogImagePathRef != null) {
                        dialogImagePathRef[0] = imagePath;
                        log.info("Updated dialog image path reference to: {}", imagePath);
                    }
                }
            } catch (Exception e) {
                log.error("Error handling picked image", e);
                Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private String copyImageToInternalStorage(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "POS_" + timeStamp + ".jpg";
        File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        File imageFile = new File(storageDir, imageFileName);
        
        FileOutputStream outputStream = new FileOutputStream(imageFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.close();
        inputStream.close();
        
        return imageFile.getAbsolutePath();
    }
    
    private void compressAndResizeImage(String imagePath) {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            bitmap = resizeBitmapForPOS(bitmap, 800, 800);
            
            FileOutputStream out = new FileOutputStream(imagePath);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out);
            out.close();
        } catch (Exception e) {
            log.error("Error compressing image", e);
        }
    }
    
    private void deleteProduct(Product product) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Product")
                .setMessage("Are you sure you want to delete \"" + product.getName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    // Get the category ID before deleting the product
                    long categoryId = product.getCategoryId();
                    
                    // Delete the product
                    database.productDao().deleteProduct(product);
                    
                    // Check if the category has any remaining products
                    List<Product> remainingProducts = database.productDao().getAllProductsByCategory(categoryId);
                    
                    // If no products remain in the category, delete the category
                    if (remainingProducts.isEmpty()) {
                        Category category = database.categoryDao().getCategoryById(categoryId);
                        if (category != null) {
                            database.categoryDao().deleteCategory(category);
                            Toast.makeText(this, "Product and empty category deleted", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Product deleted", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Product deleted", Toast.LENGTH_SHORT).show();
                    }
                    
                    loadProducts(); // This will reload and update filter
                    loadCategories(); // This will reload and update filter
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private class ProductsAdapter extends RecyclerView.Adapter<ProductsAdapter.ProductViewHolder> {
        
        @NonNull
        @Override
        public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_product, parent, false);
            return new ProductViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
            Product product = products.get(position);
            holder.bind(product);
        }
        
        @Override
        public int getItemCount() {
            return products.size();
        }
        
        class ProductViewHolder extends RecyclerView.ViewHolder {
            private TextView textName;
            private TextView textCategory;
            private TextView textPrice;
            private TextView textQuantity;
            private ImageView imageProduct;
            private Button btnEdit;
            private Button btnDelete;
            
            ProductViewHolder(View itemView) {
                super(itemView);
                textName = itemView.findViewById(R.id.text_name);
                textCategory = itemView.findViewById(R.id.text_category);
                textPrice = itemView.findViewById(R.id.text_price);
                textQuantity = itemView.findViewById(R.id.text_quantity);
                imageProduct = itemView.findViewById(R.id.image_product);
                btnEdit = itemView.findViewById(R.id.btn_edit);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
            
            void bind(Product product) {
                textName.setText(product.getName());
                
                Category category = database.categoryDao().getCategoryById(product.getCategoryId());
                if (category != null) {
                    textCategory.setText(category.getName());
                }
                
                // Convert from smallest unit to DOGE for display (1 DOGE = 100,000,000 smallest units)
                double priceInDoge = product.getPriceDoge() / 100000000.0;
                // Format to remove trailing zeros (e.g., "0.5" instead of "0.50000000")
                String formattedPrice = String.format("%.8f", priceInDoge).replaceAll("0+$", "").replaceAll("\\.$", "");
                textPrice.setText(formattedPrice + " DOGE");
                if (product.getQuantity() == -1) {
                    textQuantity.setText("Qty: Unlimited");
                } else {
                    textQuantity.setText("Qty: " + product.getQuantity());
                }
                
                if (product.getImagePath() != null && !product.getImagePath().isEmpty()) {
                    loadImagePreview(imageProduct, product.getImagePath());
                    imageProduct.setVisibility(View.VISIBLE);
                } else {
                    // Show placeholder when no image
                    imageProduct.setImageBitmap(null);
                    imageProduct.setBackgroundColor(getResources().getColor(R.color.bg_level1));
                    imageProduct.setVisibility(View.VISIBLE);
                }
                
                btnEdit.setOnClickListener(v -> showAddProductDialog(product));
                btnDelete.setOnClickListener(v -> deleteProduct(product));
            }
        }
    }
}

