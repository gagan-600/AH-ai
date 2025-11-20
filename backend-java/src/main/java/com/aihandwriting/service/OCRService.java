package com.aihandwriting.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class OCRService {

    private final ITesseract tesseract;

  public OCRService() {
    this.tesseract = new Tesseract();

    String tessDataPath = System.getenv("TESSDATA_PREFIX");
    if (tessDataPath == null || tessDataPath.isBlank()) {
      tessDataPath = "C:\\Program Files\\Tesseract-OCR\\tessdata";
    }
    this.tesseract.setDatapath(tessDataPath);
    this.tesseract.setLanguage("eng");
  }


  public String extractText(File imageFile) {
    try {
      return tesseract.doOCR(imageFile);
    } catch (TesseractException e) {
      throw new RuntimeException("OCR failed: " + e.getMessage(), e);
    } catch (UnsatisfiedLinkError e) {
      throw new RuntimeException(
        "Tesseract native libraries not found. Please install Tesseract OCR and ensure the 'TESSDATA_PREFIX' environment variable is set to the Tesseract installation directory.",
        e
      );
    }
  }
}
//tesseract "C:\Users\Aseuro\Pictures\Screenshot2.jpg" stdout 