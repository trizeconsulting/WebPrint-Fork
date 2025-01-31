/**
 * @author Tres Finocchiaro
 *
 * Copyright (C) 2013 Tres Finocchiaro, QZ Industries
 *
 * IMPORTANT:  This software is dual-licensed
 *
 * LGPL 2.1
 * This is free software.  This software and source code are released under
 * the "LGPL 2.1 License".  A copy of this license should be distributed with
 * this software. http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * QZ INDUSTRIES SOURCE CODE LICENSE
 * This software and source code *may* instead be distributed under the
 * "QZ Industries Source Code License", available by request ONLY.  If source
 * code for this project is to be made proprietary for an individual and/or a
 * commercial entity, written permission via a copy of the "QZ Industries Source
 * Code License" must be obtained first.  If you've obtained a copy of the
 * proprietary license, the terms and conditions of the license apply only to
 * the licensee identified in the agreement.  Only THEN may the LGPL 2.1 license
 * be voided.
 *
 */
package qz;

import com.sun.javafx.print.Units;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.concurrent.Worker.State;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.PrintQuality;
import javax.print.attribute.standard.PrinterResolution;
import javax.swing.JFrame;

public class PrintHTML extends JFXPanel implements Printable {

    private final AtomicReference<PrintService> ps = new AtomicReference<PrintService>(null);
    // javafx의 인쇄를 사용하여 개조
    private final AtomicReference<Printer> fxPrinter = new AtomicReference<Printer>(null);

    private final AtomicReference<String> jobName = new AtomicReference<String>("WebPrinter 2D Printing");
    private final AtomicReference<String> htmlData = new AtomicReference<String>(null);
    //private final AtomicReference<Paper> paper = new AtomicReference<Paper>(null);
    private final AtomicReference<Boolean> contentReady = new AtomicReference<Boolean>(false);

    private final AtomicInteger orientation = new AtomicInteger(PageFormat.PORTRAIT);
    private final AtomicReference<PrintPageSetting> printPageSetting = new AtomicReference<PrintPageSetting>(null);

    private WebView webView = null;
    private JFrame j = null;

    public PrintHTML() {
        super();
        super.setOpaque(true);
        super.setBackground(Color.WHITE);
        super.setBorder(null);

        j = new JFrame();
        j.setUndecorated(true);
//        j.setLayout(new FlowLayout());
        j.add(this);

        createJFXPanelWebViewScene(this);
    }

    public void createJFXPanelWebViewScene(final JFXPanel jfxPanel) {
        Platform.setImplicitExit(false); // 두 번 인쇄할 수 없는 문제 해결
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                webView = new WebView();
                webView.getEngine().getLoadWorker().stateProperty().addListener(new ChangeListener<State>() {
                    @Override
                    public void changed(ObservableValue ov, State oldState, State newState) {
                        if (newState == State.SUCCEEDED) {
                            System.out.println("WebEngine state change SUCCEEDED.");
                            contentReady.set(true);
                        }
                    }
                });
                jfxPanel.setScene(new Scene(webView));
            }
        });
    }

    public void jfxPanelWebViewLoadContent(final JFXPanel jfxPanel, final String contetnt) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                System.out.println("jfxPanelWebViewLoadContent run...");
                WebView webView = (WebView) jfxPanel.getScene().getRoot();
                webView.getEngine().loadContent(contetnt);
            }
        });
    }

    public void set(String html) {
        htmlData.set(html);
    }

    //public void append(String html) {
    //    super.setText(super.getText() == null ? html : super.getText() + html);
    // }
    // public void clear() {
    //     super.setText(null);
    // }
    public void clear() {
        htmlData.set(null);
    }

    public String get() {
        return htmlData.get();
    }

    //public String get() {
    //    return super.getText();
    //}
    public void print() {
        System.out.println("print load jxpanel content...");
        contentReady.set(false);
        jfxPanelWebViewLoadContent(this, htmlData.get());

        try {
            while (!contentReady.get()) {
                System.out.println("waiting content...");
                Thread.sleep(200);
            }
            fxWebEngingPrint();
//            framePrint();
        } catch (Exception ex) {
            Logger.getLogger(PrintHTML.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * JavaFx의 WebEngine을 사용하여 전체 웹 콘텐츠를 인쇄합니다.
     * oracle java 8을 사용하여 정상적으로 인쇄하지만 mac은 사용하지 마십시오.
     */
    private void fxWebEngingPrint() {
        javafx.print.PrinterJob printerJob = null;
        if (null != fxPrinter.get()) {
            printerJob = javafx.print.PrinterJob.createPrinterJob(fxPrinter.get());
            System.out.println("fxPrinter: " + fxPrinter.get().getName());
        } else {
            printerJob = javafx.print.PrinterJob.createPrinterJob();
            System.out.println("fxPrinter not selected");
        }

        if (null != printPageSetting.get()) {
            PageLayout defaultPageLayout = null;
            Paper paper = null;
            try {
                defaultPageLayout = printerJob.getPrinter().getDefaultPageLayout();
                paper = defaultPageLayout.getPaper();

                System.out.println("defaultPageLayout: " + defaultPageLayout.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (null != printPageSetting.get().getWidth() && null != printPageSetting.get().getHeight()) {
                Constructor<Paper> c;
                try {
                    c = Paper.class.getDeclaredConstructor(String.class, double.class, double.class, Units.class);
                    c.setAccessible(true);
                    paper = c.newInstance(printPageSetting.get().getWidth() + "x" + printPageSetting.get().getHeight(), printPageSetting.get().getWidth(), printPageSetting.get().getHeight(), Units.MM);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            if (null == paper) {
                System.out.println("paper is null or width or height need to set.");
                return;
            }

            System.out.println("paper: " + paper.toString());

            if (null == printPageSetting.get().getLeftMargin()) {
                printPageSetting.get().setLeftMargin(null != defaultPageLayout ? defaultPageLayout.getLeftMargin() : 0);
            }
            if (null == printPageSetting.get().getRightMargin()) {
                printPageSetting.get().setRightMargin(null != defaultPageLayout ? defaultPageLayout.getRightMargin() : 0);
            }
            if (null == printPageSetting.get().getTopMargin()) {
                printPageSetting.get().setTopMargin(null != defaultPageLayout ? defaultPageLayout.getTopMargin() : 0);
            }
            if (null == printPageSetting.get().getBottomMargin()) {
                printPageSetting.get().setBottomMargin(null != defaultPageLayout ? defaultPageLayout.getBottomMargin() : 0);
            }

            System.out.println("printPageSetting: " + printPageSetting.get().getLeftMargin() + ", " + printPageSetting.get().getRightMargin() + ", " + printPageSetting.get().getTopMargin() + ", " + printPageSetting.get().getBottomMargin());

            PageLayout newPageLayout = printerJob.getPrinter().createPageLayout(paper, null != defaultPageLayout ? defaultPageLayout.getPageOrientation() : PageOrientation.PORTRAIT, printPageSetting.get().getLeftMargin(), printPageSetting.get().getRightMargin(), printPageSetting.get().getTopMargin(), printPageSetting.get().getBottomMargin());
            System.out.println("newPageLayout: " + newPageLayout.toString());

            printerJob.getJobSettings().setPageLayout(newPageLayout);
        } else {
            System.out.println("no need to set PageLayout");
        }

        printerJob.getJobSettings().setJobName(jobName.get());
        if (printerJob != null) {
            this.webView.getEngine().print(printerJob);
            printerJob.endJob();
        }
    }

    /**
     * awt를 사용하여 컨트롤의 이미지를 인쇄하면
     * jxpanel에 의해 표시된 부분만 인쇄할 수 있지만
     * 전체 웹 페이지는 인쇄할 수 없습니다.
     *
     * @throws PrinterException
     */
    private void framePrint() throws PrinterException {
        System.out.println("print begin ...");
        j.setTitle(jobName.get());

        j.pack();
        j.setExtendedState(j.ICONIFIED);
        j.setVisible(true);

        // Elimate any margins
        HashPrintRequestAttributeSet attr = new HashPrintRequestAttributeSet();
        attr.add(new PrinterResolution(300, 300, PrinterResolution.DPI));
        attr.add(new MediaPrintableArea(0f, 0f, getWidth() / 72f, getHeight() / 72f, MediaPrintableArea.INCH));
        attr.add(PrintQuality.HIGH);
//        attr.add(Fidelity.FIDELITY_TRUE);
        System.out.println("print ...W-" + getWidth() / 72f + ",H-" + getHeight() / 72f);

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(ps.get());
        job.setPrintable(this);
        job.setJobName(jobName.get());
        job.print(attr);
//        j.setVisible(false);

//        j.dispose();
        clear();
        System.out.println("print end.");
    }

    public void setPrintParameters(PrintManager a) {
        this.ps.set(a.getPrintService());
        this.jobName.set(a.getJobName().replace(" ___ ", " HTML "));
    }

    public void setPrintService(PrintService ps) {
        this.ps.set(ps);
    }

    public void setPrinter(Printer selectedPrinter) {
        this.fxPrinter.set(selectedPrinter);
    }

    public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
        if (g == null) {
            throw new PrinterException("No graphics specified");
        }
        if (pf == null) {
            throw new PrinterException("No page format specified");
        }
        if (pageIndex > 0) {
            return (NO_SUCH_PAGE);
        }

        System.out.println("print: p-" + pageIndex + ", w-" + pf.getWidth() + ", h-" + pf.getHeight() + ", ori-" + pf.getOrientation());

        boolean doubleBuffered = super.isDoubleBuffered();
        super.setDoubleBuffered(false);

//        pf.setOrientation(orientation.get());
        //Paper paper = new Paper();
        //paper.setSize(8.5 * 72, 11 * 72);
        //paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());
        //pf.setPaper(paper);
        //Paper paper = pf.getPaper();
        //paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());
        //pf.getPaper().setImageableArea(0, 0, paper.getWidth() + 200, paper.getHeight() + 200);
        //pf.getPaper().setImageableArea(-100, -100, 200, 200);
        Graphics2D g2d = (Graphics2D) g;
        final AffineTransform trans = g2d.getDeviceConfiguration().getNormalizingTransform();
        System.out.println(trans.getScaleX() * 72 + " DPI horizontally");
        System.out.println(trans.getScaleY() * 72 + " DPI vertically");

        g2d.translate(pf.getImageableX(), pf.getImageableY());
        //g2d.translate(paper.getImageableX(), paper.getImageableY());
        double dpiScale = trans.getScaleY() * 72 / 300.0;
        g2d.scale(dpiScale, dpiScale);
        this.getParent().paint(g2d);
        super.setDoubleBuffered(doubleBuffered);
        return (PAGE_EXISTS);
    }

    public void setPrintPageSetting(PrintPageSetting pageSetting) {
        printPageSetting.set(pageSetting);
    }
}
