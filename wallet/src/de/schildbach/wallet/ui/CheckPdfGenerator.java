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

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import de.schildbach.wallet.R;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.data.Check;
import de.schildbach.wallet.util.Qr;

import org.bitcoinj.core.Coin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for generating PDF check documents
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class CheckPdfGenerator {
    private static final Logger log = LoggerFactory.getLogger(CheckPdfGenerator.class);
    
    public static void generatePdf(Context context, Check check) {
        PdfDocument document = null;
        try {
            log.info("Starting PDF generation for check ID: {}", check.getId());
            
            // Create PDF document
            document = new PdfDocument();
            
            // Page info - High resolution for better quality, especially QR codes
            // Scale factor: 3x for high resolution (6.5" x 3" at 216 DPI = 1404 x 648 points)
            // This ensures QR codes and text are crisp and readable
            int scaleFactor = 3;
            int pageWidth = 468 * scaleFactor;  // 1404 points
            int pageHeight = 216 * scaleFactor;  // 648 points
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();
            
            // Don't scale canvas - scale all measurements proportionally instead
            // Background color (light yellow/cream for check appearance)
            canvas.drawColor(Color.rgb(255, 255, 240));
            
            // Draw check design with all measurements scaled proportionally
            drawCheckDesign(canvas, check, context, scaleFactor);
            
            document.finishPage(page);
            log.info("PDF page finished");
            
            // Save PDF
            File pdfFile = savePdf(context, document, check);
            log.info("PDF saved to: {}", pdfFile.getAbsolutePath());
            
            // Close document before opening
            document.close();
            document = null;
            
            // Open PDF
            openPdf(context, pdfFile);
            
            Toast.makeText(context, "Check PDF generated successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            log.error("IO error generating PDF", e);
            if (document != null) {
                try {
                    document.close();
                } catch (Exception e2) {
                    log.error("Error closing document", e2);
                }
            }
            Toast.makeText(context, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            log.error("Error generating PDF", e);
            if (document != null) {
                try {
                    document.close();
                } catch (Exception e2) {
                    log.error("Error closing document", e2);
                }
            }
            Toast.makeText(context, "Error generating PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private static void drawCheckDesign(Canvas canvas, Check check, Context context, int scaleFactor) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        
        // Use Comic Sans-like font (SANS_SERIF is casual and similar)
        Typeface comicSansLike = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
        Typeface comicSansLikeBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        
        // Define margins and layout constants - all scaled proportionally
        float leftMargin = 25 * scaleFactor;
        float leftContentMargin = 40 * scaleFactor; // Left content moved more to the right
        float rightMargin = 25 * scaleFactor;
        float topMargin = 25 * scaleFactor;
        float bottomMargin = 25 * scaleFactor;
        float borderWidth = 2 * scaleFactor;
        float pageWidth = 468 * scaleFactor;
        float pageHeight = 216 * scaleFactor;
        float contentWidth = pageWidth - leftMargin - rightMargin;
        float contentHeight = pageHeight - topMargin - bottomMargin;
        float borderLeft = leftMargin - borderWidth/2;
        float borderTop = topMargin - borderWidth/2;
        float borderRight = pageWidth - rightMargin + borderWidth/2;
        float borderBottom = pageHeight - bottomMargin + borderWidth/2;
        
        // Draw border (rectangular check format)
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(borderWidth);
        canvas.drawRect(borderLeft, borderTop, borderRight, borderBottom, paint);
        
        // Draw decorative top border line (micr line)
        paint.setStrokeWidth(0.5f * scaleFactor);
        paint.setColor(Color.rgb(100, 100, 100));
        float micrLineY = topMargin + 20 * scaleFactor;
        canvas.drawLine(leftMargin, micrLineY, pageWidth - rightMargin, micrLineY, paint);
        
        // Draw Dogecoin logo image before the name
        float logoX = leftContentMargin;
        float logoSize = 26 * scaleFactor; // Scaled proportionally
        // Align logo vertically with text: text baseline is at topMargin + 15*scaleFactor, text size is 18*scaleFactor
        float logoY = topMargin - 1 * scaleFactor; // Scaled proportionally
        try {
            Drawable logoDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_app_color_48dp);
            if (logoDrawable != null) {
                Bitmap logoBitmap = drawableToBitmap(logoDrawable, (int)logoSize, (int)logoSize);
                if (logoBitmap != null) {
                    canvas.drawBitmap(logoBitmap, logoX, logoY, paint);
                    logoX += logoSize + 6 * scaleFactor; // Position text after logo with spacing
                }
            }
        } catch (Exception e) {
            log.warn("Error loading Dogecoin logo", e);
        }
        
        // Draw Dogecoin name - header
        paint.setColor(Color.rgb(196, 181, 80)); // Dogecoin gold color
        paint.setTextSize(18 * scaleFactor); // Scaled proportionally
        paint.setTypeface(comicSansLikeBold);
        paint.setLetterSpacing(0.1f); // Letter spacing is relative, keep same
        canvas.drawText("DOGECOIN", logoX, topMargin + 15 * scaleFactor, paint);
        
        // Draw check number (using check ID) - top right
        paint.setColor(Color.BLACK);
        paint.setTextSize(10 * scaleFactor); // Scaled proportionally
        paint.setTypeface(comicSansLike);
        paint.setLetterSpacing(0.05f);
        String checkNum = "Check #" + check.getId();
        float checkNumWidth = paint.measureText(checkNum);
        float checkNumPadding = 10 * scaleFactor; // Scaled proportionally
        canvas.drawText(checkNum, pageWidth - rightMargin - checkNumWidth - checkNumPadding, topMargin + 15 * scaleFactor, paint);
        
        // Draw date - below header
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        paint.setTextSize(11 * scaleFactor); // Scaled proportionally
        paint.setTypeface(comicSansLike);
        paint.setLetterSpacing(0.05f);
        String dateStr = dateFormat.format(check.getDate());
        canvas.drawText(dateStr, leftContentMargin, topMargin + 35 * scaleFactor, paint);
        
        // Draw expiration date - below date, before "Pay to the order of"
        if (check.getExpirationDate() != null) {
            paint.setTextSize(7 * scaleFactor); // Smaller font size
            paint.setTypeface(comicSansLike);
            paint.setLetterSpacing(0.05f);
            String expirationStr = "Expires: " + dateFormat.format(check.getExpirationDate());
            canvas.drawText(expirationStr, leftContentMargin, topMargin + 45 * scaleFactor, paint);
        }
        
        // Calculate available space for left content (leaving room for QR code on right)
        float leftContentWidth = contentWidth - 100 * scaleFactor; // Reserve space for QR code area
        float rightContentStart = pageWidth - rightMargin - 90 * scaleFactor; // QR code area starts here
        
        // Draw "Pay to the order of" - left side
        paint.setTextSize(10 * scaleFactor); // Scaled proportionally
        paint.setTypeface(comicSansLike);
        paint.setLetterSpacing(0.08f);
        canvas.drawText("Pay to the order of", leftContentMargin, topMargin + 55 * scaleFactor, paint);
        
        // Draw pay to name - bold, larger, with letter spacing
        paint.setTextSize(15 * scaleFactor); // Scaled proportionally
        paint.setTypeface(comicSansLikeBold);
        paint.setLetterSpacing(0.1f);
        String payTo = check.getPayTo();
        // Truncate if too long
        float maxPayToWidth = leftContentWidth - (leftContentMargin - leftMargin);
        if (paint.measureText(payTo) > maxPayToWidth) {
            while (paint.measureText(payTo + "...") > maxPayToWidth && payTo.length() > 0) {
                payTo = payTo.substring(0, payTo.length() - 1);
            }
            payTo = payTo + "...";
        }
        canvas.drawText(payTo, leftContentMargin, topMargin + 75 * scaleFactor, paint);
        
        // Draw amount in words - left side
        Coin amount = Coin.valueOf(check.getAmount());
        String amountText = amount.toPlainString() + " DOGE";
        paint.setTextSize(10 * scaleFactor); // Scaled proportionally
        paint.setTypeface(comicSansLike);
        paint.setLetterSpacing(0.08f);
        String amountWords = amountText;
        if (paint.measureText(amountWords) > maxPayToWidth) {
            while (paint.measureText(amountWords + "...") > maxPayToWidth && amountWords.length() > 0) {
                amountWords = amountWords.substring(0, amountWords.length() - 1);
            }
            amountWords = amountWords + "...";
        }
        canvas.drawText(amountWords, leftContentMargin, topMargin + 95 * scaleFactor, paint);
        
        // Draw amount in numbers - centered horizontally and vertically
        paint.setTextSize(17 * scaleFactor); // Scaled proportionally
        paint.setTypeface(comicSansLikeBold);
        paint.setLetterSpacing(0.1f);
        String amountDisplay = "Ð" + amountText; // Use Dogecoin symbol instead of $
        float amountWidth = paint.measureText(amountDisplay);
        // Center horizontally: (leftMargin + rightContentStart) / 2 - amountWidth / 2
        float centerX = (leftContentMargin + rightContentStart) / 2 - amountWidth / 2;
        // Center vertically: approximately middle of check content area
        float centerY = topMargin + (contentHeight / 2) + 8 * scaleFactor; // Scaled proportionally
        canvas.drawText(amountDisplay, centerX, centerY, paint);
        
        // Draw memo if present - left side, below amount
        float currentY = topMargin + 115 * scaleFactor;
        if (check.getMemo() != null && !check.getMemo().trim().isEmpty()) {
            paint.setTextSize(9 * scaleFactor); // Scaled proportionally
            paint.setTypeface(comicSansLike);
            paint.setLetterSpacing(0.06f);
            String memo = "Memo: " + check.getMemo();
            if (paint.measureText(memo) > maxPayToWidth) {
                String truncatedMemo = memo;
                while (paint.measureText(truncatedMemo + "...") > maxPayToWidth && truncatedMemo.length() > 0) {
                    truncatedMemo = truncatedMemo.substring(0, truncatedMemo.length() - 1);
                }
                memo = truncatedMemo + "...";
            }
            canvas.drawText(memo, leftContentMargin, currentY, paint);
            currentY += 18 * scaleFactor; // Scaled proportionally
        }
        
        // Draw signature line - left side
        paint.setTextSize(9 * scaleFactor); // Scaled proportionally
        paint.setTypeface(comicSansLike);
        paint.setLetterSpacing(0.06f);
        String signature = check.getSignature();
        if (paint.measureText(signature) > maxPayToWidth) {
            while (paint.measureText(signature + "...") > maxPayToWidth && signature.length() > 0) {
                signature = signature.substring(0, signature.length() - 1);
            }
            signature = signature + "...";
        }
        canvas.drawText("Sig: " + signature, leftContentMargin, currentY, paint);
        
        // Draw QR code for private key - right side, properly positioned inside border
            try {
            // Generate QR code at high resolution for crisp rendering
            int qrSize = 70 * scaleFactor; // Scaled proportionally (210 pixels at 3x scale)
            
            // Generate QR code with private key, P2SH CLTV address, and locktime
            // Format: "WIF_KEY|P2SH_ADDRESS|LOCKTIME" so sweep wallet can query both addresses and reconstruct CLTV script
            // Ensure the derived key is trimmed (no whitespace) for proper QR code scanning
            String keyForQr = check.getDerivedKey();
            String p2shAddress = check.getAddress();
            if (keyForQr != null) {
                keyForQr = keyForQr.trim(); // Remove any leading/trailing whitespace
                
                // Include P2SH address and locktime if available (for CLTV checks)
                if (p2shAddress != null && !p2shAddress.trim().isEmpty() && check.getDate() != null) {
                    // Calculate locktime from check date (Unix timestamp in seconds)
                    long lockTime = check.getDate().getTime() / 1000;
                    String qrData = keyForQr + "|" + p2shAddress.trim() + "|" + lockTime;
                    keyForQr = qrData;
                    log.info("Generating QR code with private key, P2SH address, and locktime (key length: {}, address: {}, locktime: {})", 
                        check.getDerivedKey().length(), p2shAddress, lockTime);
                } else {
                    log.info("Generating QR code for private key only (length: {}, starts with: {})", 
                        keyForQr.length(), keyForQr.substring(0, Math.min(5, keyForQr.length())));
                }
            }
            Bitmap qrBitmap = Qr.bitmap(keyForQr);
                if (qrBitmap != null) {
                // Scale to high resolution for crisp rendering in PDF
                qrBitmap = Bitmap.createScaledBitmap(qrBitmap, qrSize, qrSize, true);
                
                float qrX = rightContentStart;
                float qrY = topMargin + 50 * scaleFactor; // Start QR code below header, scaled
                
                // Ensure QR code doesn't go outside border
                if (qrY + qrSize > borderBottom - 5 * scaleFactor) {
                    qrY = borderBottom - qrSize - 5 * scaleFactor;
                }
                if (qrX + qrSize > borderRight - 5 * scaleFactor) {
                    qrX = borderRight - qrSize - 5 * scaleFactor;
                }
                
                // Draw high-resolution QR code
                canvas.drawBitmap(qrBitmap, qrX, qrY, paint);
                    
                // Label for QR code - below QR code, inside border
                paint.setTextSize(7 * scaleFactor); // Scaled proportionally
                paint.setTypeface(comicSansLike);
                paint.setLetterSpacing(0.05f);
                String qrLabel = "QR Code";
                float qrLabelWidth = paint.measureText(qrLabel);
                float qrLabelX = qrX + (qrSize / 2) - (qrLabelWidth / 2);
                float qrLabelY = qrY + qrSize + 10 * scaleFactor; // Scaled proportionally
                
                // Ensure label is inside border
                if (qrLabelY < borderBottom - 5 * scaleFactor) {
                    canvas.drawText(qrLabel, qrLabelX, qrLabelY, paint);
                }
                }
            } catch (Exception e) {
                log.warn("Error generating QR code", e);
            }
        
        // Draw address - bottom, very small, inside border
        paint.setTextSize(7 * scaleFactor); // Scaled proportionally
        paint.setTypeface(comicSansLike);
        paint.setLetterSpacing(0.04f);
        String address = check.getAddress();
        float maxAddressWidth = contentWidth - 20 * scaleFactor; // Scaled proportionally
        // Truncate address if too long
        if (paint.measureText(address) > maxAddressWidth) {
            String truncated = address.substring(0, 25) + "...";
            canvas.drawText(truncated, leftContentMargin, borderBottom - 8 * scaleFactor, paint);
        } else {
            canvas.drawText(address, leftContentMargin, borderBottom - 8 * scaleFactor, paint);
        }
        }
        
    /**
     * Convert a Drawable to a Bitmap
     */
    private static Bitmap drawableToBitmap(Drawable drawable, int width, int height) {
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return Bitmap.createScaledBitmap(bitmapDrawable.getBitmap(), width, height, true);
            }
        }
        
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }
    
    private static File savePdf(Context context, PdfDocument document, Check check) throws IOException {
        // Create directory if it doesn't exist
        File externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (externalFilesDir == null) {
            throw new IOException("External files directory is not available");
        }
        
        File dir = new File(externalFilesDir, "Checks");
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created && !dir.exists()) {
                throw new IOException("Failed to create Checks directory: " + dir.getAbsolutePath());
            }
        }
        
        // Create file
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String fileName = "Check_" + check.getId() + "_" + dateFormat.format(new Date()) + ".pdf";
        File file = new File(dir, fileName);
        
        log.info("Saving PDF to: {}", file.getAbsolutePath());
        
        // Write PDF
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
        document.writeTo(fos);
            fos.flush();
            log.info("PDF written successfully, file size: {} bytes", file.length());
        } finally {
            if (fos != null) {
                try {
        fos.close();
                } catch (IOException e) {
                    log.error("Error closing file output stream", e);
                }
            }
        }
        
        if (!file.exists() || file.length() == 0) {
            throw new IOException("PDF file was not created or is empty: " + file.getAbsolutePath());
        }
        
        return file;
    }
    
    private static void openPdf(Context context, File pdfFile) {
        try {
            if (!pdfFile.exists()) {
                log.error("PDF file does not exist: {}", pdfFile.getAbsolutePath());
                Toast.makeText(context, "Error: PDF file not found", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Uri uri;
            try {
                // Try FileProvider first
                uri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".file_attachment",
                    pdfFile
                );
                log.info("Created FileProvider URI: {}", uri);
            } catch (IllegalArgumentException e) {
                log.warn("FileProvider failed, trying fallback: {}", e.getMessage());
                // Fallback to file URI (may not work on newer Android versions)
                uri = Uri.fromFile(pdfFile);
                log.info("Using fallback file URI: {}", uri);
            }
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Use chooser to let user select PDF viewer
            Intent chooser = Intent.createChooser(intent, "Open PDF");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // Also add the URI to the chooser's clip data to ensure permissions are passed
            chooser.setClipData(android.content.ClipData.newRawUri("PDF", uri));
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            log.info("Starting PDF viewer chooser");
            context.startActivity(chooser);
        } catch (android.content.ActivityNotFoundException e) {
            log.error("No PDF viewer app found", e);
            Toast.makeText(context, "No PDF viewer app found. Please install a PDF viewer.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            log.error("Error opening PDF", e);
            Toast.makeText(context, "Error opening PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}

