/* BrailleBlaster Braille Transcription Application
 *
 * Copyright (C) 2010, 2012
 * ViewPlus Technologies, Inc. www.viewplus.com
 * and
 * Abilitiessoft, Inc. www.abilitiessoft.com
 * All rights reserved
 *
 * This file may contain code borrowed from files produced by various 
 * Java development teams. These are gratefully acknoledged.
 *
 * This file is free software; you can redistribute it and/or modify it
 * under the terms of the Apache 2.0 License, as given at
 * http://www.apache.org/licenses/
 *
 * This file is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE
 * See the Apache 2.0 License for more details.
 *
 * You should have received a copy of the Apache 2.0 License along with 
 * this program; see the file LICENSE.
 * If not, see
 * http://www.apache.org/licenses/
 *
 * Maintained by John J. Boyer john.boyer@abilitiessoft.com
 */

package org.brailleblaster.wordprocessor;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.layout.FormLayout;
import org.brailleblaster.BBIni;
import org.eclipse.swt.printing.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.custom.StyledText;
import nu.xom.*;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.liblouis.liblouisutdml;
import org.brailleblaster.util.Notify;
import java.io.File;
import org.daisy.printing.*;
import javax.print.PrintException;
import org.eclipse.swt.widgets.Listener;
import org.brailleblaster.settings.Welcome;
import org.eclipse.swt.widgets.MessageBox;

/**
 * This class encapsulates handling of the Universal 
 * TactileDocument Markup Language (UTDML);
 */
class UTD {

    //static int pageCount;
    //static int pageCount2;

    int braillePageNumber; //number of braille pages
    String firstTableName;
    int dpi; // resolution
    int paperWidth;
    int paperHeight;
    int leftMargin;
    int rightMargin;
    int topMargin;
    int bottomMargin;
    String currentBraillePageNumber;
    String currentPrintPageNumber;
    int[] brlIndex;
    int brlIndexPos;
    int[] brlonlyIndex;
    int brlonlyIndexPos;
    Node beforeBrlNode;
    Node beforeBrlonlyNode;
    private boolean firstPage;
    private boolean firstLineOnPage;
    StringBuilder brailleLine = new StringBuilder (4096);
    StringBuilder printLine = new StringBuilder (4096);
    DocumentManager dm;
    Document doc;
    //for threading
    int numLines;
    int numPages;
    int numChars;


    UTD (final DocumentManager dm) {
        this.dm = dm;
    }

    void displayTranslatedFile() {
        beforeBrlNode = null;
        beforeBrlonlyNode = null;
        brlIndex = null;
        brlIndexPos = 0;
        brlonlyIndex = null;
        brlonlyIndexPos = 0;
        firstPage = true;
        firstLineOnPage = true;
        braillePageNumber = 0; //number of braille pages
        firstTableName = null;
        dpi = 0; // resolution
        paperWidth = 0;
        paperHeight = 0;
        leftMargin = 0;
        rightMargin = 0;
        topMargin = 0;
        bottomMargin = 0;
        currentBraillePageNumber = "0";
        currentPrintPageNumber = "0";
        Builder parser = new Builder();
        try {
            doc = parser.build (dm.translatedFileName);
        } catch (ParsingException e) {
            new Notify ("Malformed document");
            return;
        }
        catch (IOException e) {
            System.out.println("Could not open"+ dm.translatedFileName+" because" + e.getMessage());
            new Notify ("Could not open " + dm.translatedFileName);
            return;
        }
        final Element rootElement = doc.getRootElement();
        //Use threading to keep the control of the window
        new Thread() {
            public void run() {
                findBrlNodes (rootElement);
            }
        }
        .start();
    }

    private void findBrlNodes (Element node) {
        Node newNode;
        Element element;
        String elementName;
        for (int i = 0; i < node.getChildCount(); i++) {
            newNode = node.getChild(i);
            if (newNode instanceof Element) {
                element = (Element)newNode;
                elementName = element.getLocalName();
                if (elementName.equals ("meta")) {
                    doUtdMeta (element);
                } else if (elementName.equals ("brl")) {
                    if (i > 0) {
                        try{
                            beforeBrlNode = newNode.getChild(i - 1);
                        }
                        catch(IndexOutOfBoundsException e ){
                            //The brl child, newNode, may not have any grandchild of node
                            System.out.println("a brl Node does not have child, i = "+i ); 
                            beforeBrlNode = null; 
                            return;
                        }
                    } else {
                        beforeBrlNode = null;
                    }
                    doBrlNode (element);
                } else {
                    findBrlNodes (element);
                }
            }
            if(brailleLine.length()>2048 || printLine.length()>2048 || i== node.getChildCount()-1){
                dm.display.syncExec(new Runnable() {    
                    public void run() {                        
                        dm.braille.view.append(brailleLine.toString());
                        numChars += brailleLine.length();
                        brailleLine.delete (0, brailleLine.length());
                        dm.daisy.view.append(printLine.toString());
                        printLine.delete (0, printLine.length());
                        dm.statusBar.setText ("Translated " + numPages +" pages, " + numLines + " lines, " + numChars 
                                + " characters.");
                    }
                });        
            }

        }
    }

    private void doUtdMeta (Element node) {
        if (braillePageNumber != 0) {
            return;
        }
        String metaContent;
        metaContent = node.getAttributeValue ("name");
        if (!(metaContent.equals ("utd"))) {
            return;
        }
        metaContent = node.getAttributeValue ("content");
        String[] keysValues = metaContent.split (" ", 20);
        for (int i = 0; i < keysValues.length; i++) {
            String keyValue[] = keysValues[i].split ("=", 2);
            if (keyValue[0].equals ("BraillePageNumber"))
                braillePageNumber = Integer.parseInt (keyValue[1]);
            else if (keyValue[0].equals ("firstTableName"))
                firstTableName = keyValue[1];
            else if (keyValue[0].equals ("dpi"))
                dpi = Integer.parseInt (keyValue[1]);
            else if (keyValue[0].equals ("paperWidth"))
                paperWidth = Integer.parseInt (keyValue[1]);
            else if (keyValue[0].equals ("paperHeight"))
                paperHeight = Integer.parseInt (keyValue[1]);
            else if (keyValue[0].equals ("leftMargin"))
                leftMargin = Integer.parseInt (keyValue[1]);
            else if (keyValue[0].equals ("rightMargin"))
                rightMargin = Integer.parseInt (keyValue[1]);
            else if (keyValue[0].equals ("topMargin"))
                topMargin = Integer.parseInt (keyValue[1]);
            else if (keyValue[0].equals ("bottomMargin"))
                bottomMargin = Integer.parseInt (keyValue[1]);
        }
        return;
    }

    void showLines () {
        brailleLine.append ("\n");
        printLine.append ("\n");
    }

    private void doBrlNode (Element node) {
        String tmp = node.getAttributeValue("index");
        String[] indices = null;
        if(tmp != null) indices = node.getAttributeValue ("index").split (" ", 20000);
        if (indices != null) {
            brlIndex = new int[indices.length];
            for (int i = 0; i < indices.length; i++) {
                brlIndex[i] = Integer.parseInt (indices[i]);
            }
        }
        brlIndexPos = 0;
        indices = null;
        Node newNode;
        Element element;
        String elementName;
        for (int i = 0; i < node.getChildCount(); i++) {
            newNode = node.getChild(i);
            if (newNode instanceof Element) {
                element = (Element)newNode;
                elementName = element.getLocalName();
                if (elementName.equals ("newpage")) {
                    //page number is updated in doNewpage
                    doNewpage (element);
                } else if (elementName.equals ("newline")) {
                    numLines++;
                    doNewline (element);
                } else if (elementName.equals ("span")) {
                    doSpanNode (element);
                } else if (elementName.equals ("graphic")) {
                    doGraphic (element);
                }
            }
            else if (newNode instanceof Text) {
                doTextNode (newNode);
            }
        }
        finishBrlNode();
        brlIndex = null;
        brlIndexPos = 0;
    }

    private void doSpanNode (Element node) {
        String whichSpan = node.getAttributeValue ("class");
        if (whichSpan.equals ("brlonly")) {
            doBrlonlyNode (node);
        }
        else if (whichSpan.equals ("locked")) {
            doLockedNode (node);
        }
    }

    private void doBrlonlyNode (Element node) {
        Node newNode;
        Element element;
        String elementName;
        for (int i = 0; i < node.getChildCount(); i++) {
            newNode = node.getChild(i);
            if (newNode instanceof Element) {
                element = (Element)newNode;
                elementName = element.getLocalName();
                if (elementName.equals ("brl")) {
                    insideBrlonly (node);
                }
            }
            else if (newNode instanceof Text) {
                beforeBrlonlyNode = newNode;
            }
        }
    }

    private void insideBrlonly (Element node) {
        String tmp = node.getAttributeValue("index");
        String[] indices = null;
        if(tmp != null) indices = tmp.split (" ", 20000);
        if (indices != null) {
            brlonlyIndex = new int[indices.length];
            for (int i = 0; i < indices.length; i++) {
                brlonlyIndex[i] = Integer.parseInt (indices[i]);
            }
        }
        brlonlyIndexPos = 0;
        indices = null;
        Node newNode;
        Element element;
        String elementName;
        for (int i = 0; i < node.getChildCount(); i++) {
            newNode = node.getChild(i);
            if (newNode instanceof Element) {
                element = (Element)newNode;
                elementName = element.getLocalName();
                if (elementName.equals ("newpage")) {
                    doNewpage (element);
                } else if (elementName.equals ("newline")) {
                    doNewline (element);
                } else if (elementName.equals ("graphic")) {
                    doGraphic (element);
                }
            }
            else if (newNode instanceof Text) {
                doBrlonlyTextNode (newNode);
            }
        }
        brlonlyIndex = null;
        brlonlyIndexPos = 0;
    }

    private void doLockedNode (Element node) {
    }

    private void doNewpage (Element node) {
        String pageNumber = node.getAttributeValue ("brlnumber");
        System.out.print("doNewpage: pageNumber = " +pageNumber+", Thread="+ Thread.currentThread().getName());
        if(pageNumber != null) {
            currentBraillePageNumber = pageNumber;
            numPages++;
        }
        pageNumber = node.getAttributeValue ("printnumber");
        if(pageNumber!= null) currentPrintPageNumber =pageNumber; 
        System.out.println("...done");
        //this may need to be reconsidered
        firstLineOnPage = true;
        if (firstPage) {
            firstPage = false;
            return;
        }
        showLines();
    }

    private void doNewline (Element node) {
        String positions = node.getAttributeValue ("xy");
        if (positions != null) {
            String[] horVertPos = positions.split (",", 2);
        }
        if (firstLineOnPage) {
            firstLineOnPage = false;
            return;
        }
        showLines();
    }

    private void doTextNode (Node node) {
        Text text = (Text)node;
        brailleLine.append (text.getValue());
    }

    private void doBrlonlyTextNode (Node node) {
        Text text = (Text)node;
        brailleLine.append (text.getValue());
    }

    private void doGraphic (Element node) {
    }

    private void finishBrlNode() {
        return;
    }

}