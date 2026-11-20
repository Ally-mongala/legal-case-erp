package za.co.mongala.legal.backend.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Deadline;
import za.co.mongala.legal.model.Extraction;
import za.co.mongala.legal.model.LegalDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a multi-sheet Microsoft Excel (.xlsx) ERP & AI Intelligence workbook
 * using Apache POI (Outcome D.1.5 interoperability):
 *   Sheet 1 - Matters & Finance (fee notes, settled amounts, Section 86 trust, AI claim quantum)
 *   Sheet 2 - AI Extractions & Quality (structured NLP fields + confidence scores from PDF/Word pleadings)
 *   Sheet 3 - Billing & 15% VAT Ledger (every time & disbursement item across the firm)
 *   Sheet 4 - Trust & Deadlines (Section 86 trust ledger + court/filing deadlines)
 */
public class ExcelWorkbookService {

    public byte[] generateFirmWorkbook(CaseService caseService,
                                       BillingService billingService,
                                       TrustAccountingService trustService,
                                       DocumentService documentService,
                                       ExtractionService extractionService,
                                       DeadlineService deadlineService) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle moneyStyle = createCurrencyStyle(wb, false);
            CellStyle boldMoneyStyle = createCurrencyStyle(wb, true);
            CellStyle boldTextStyle = createBoldStyle(wb);
            CellStyle pctStyle = createPercentStyle(wb);

            List<Case> allCases = caseService.search(null);
            Map<String, Case> caseMap = new HashMap<>();
            for (Case c : allCases) caseMap.put(c.getId(), c);

            var overview = billingService.firmOverview(caseService, trustService, documentService, extractionService);

            buildMattersSheet(wb, overview, allCases, headerStyle, moneyStyle, boldMoneyStyle, boldTextStyle);
            buildAiExtractionsSheet(wb, allCases, documentService, extractionService, headerStyle, pctStyle);
            buildBillingSheet(wb, billingService.allEntries(), caseMap, headerStyle, moneyStyle, boldMoneyStyle, boldTextStyle);
            buildTrustAndDeadlinesSheet(wb, trustService.allEntries(), deadlineService.all(), caseMap, headerStyle, moneyStyle, boldTextStyle);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate Excel (.xlsx) ERP workbook", e);
        }
    }

    private void buildMattersSheet(Workbook wb, BillingService.FirmBillingOverview overview, List<Case> allCases,
                                   CellStyle headerStyle, CellStyle moneyStyle, CellStyle boldMoneyStyle, CellStyle boldTextStyle) {
        Sheet s = wb.createSheet("Matters & Finance");
        String[] cols = {
                "Reference", "Title", "Client", "Opposing Party", "Practice Area", "Court",
                "Case Status", "Opened Date", "Claim Quantum (ZAR)", "Quantum Source",
                "Hours", "Billed incl. 15% VAT (ZAR)", "Settled (ZAR)", "Outstanding (ZAR)",
                "Fee / Claim %", "Trust Credit s86 (ZAR)", "Invoice Status"
        };
        writeHeader(s, 0, cols, headerStyle);

        Map<String, Case> byId = new HashMap<>();
        for (Case c : allCases) byId.put(c.getId(), c);

        int rowIdx = 1;
        for (var m : overview.matters()) {
            Case c = byId.get(m.caseId());
            Row r = s.createRow(rowIdx++);
            r.createCell(0).setCellValue(safe(m.reference()));
            r.createCell(1).setCellValue(safe(m.title()));
            r.createCell(2).setCellValue(safe(m.clientName()));
            r.createCell(3).setCellValue(c == null ? "" : safe(c.getOpposingParty()));
            r.createCell(4).setCellValue(c == null ? "" : safe(c.getMatterType()));
            r.createCell(5).setCellValue(c == null ? "" : safe(c.getCourt()));
            r.createCell(6).setCellValue(safe(m.status()));
            r.createCell(7).setCellValue(c == null || c.getOpenedDate() == null ? "" : c.getOpenedDate().toString());
            setMoney(r, 8, m.effectiveClaimAmount(), moneyStyle);
            r.createCell(9).setCellValue(safe(m.claimSource()).replace('_', ' '));
            r.createCell(10).setCellValue(m.totalHours());
            setMoney(r, 11, m.totalInclVat(), moneyStyle);
            setMoney(r, 12, m.totalSettled(), moneyStyle);
            setMoney(r, 13, m.outstandingBalance(), moneyStyle);
            r.createCell(14).setCellValue(m.feeToClaimPct() + "%");
            setMoney(r, 15, m.availableTrustBalance(), moneyStyle);
            r.createCell(16).setCellValue(safe(m.paymentStatus()).replace('_', ' '));
        }

        Row tot = s.createRow(rowIdx + 1);
        Cell lbl = tot.createCell(0);
        lbl.setCellValue("FIRM TOTALS");
        lbl.setCellStyle(boldTextStyle);
        Cell hrs = tot.createCell(10);
        hrs.setCellValue(overview.firmHours());
        hrs.setCellStyle(boldTextStyle);
        setMoney(tot, 11, overview.firmBilledInclVat(), boldMoneyStyle);
        setMoney(tot, 12, overview.firmTotalSettled(), boldMoneyStyle);
        setMoney(tot, 13, overview.firmOutstanding(), boldMoneyStyle);
        setMoney(tot, 15, overview.firmTrustHeld(), boldMoneyStyle);

        autoSize(s, cols.length);
    }

    private void buildAiExtractionsSheet(Workbook wb, List<Case> allCases, DocumentService documentService,
                                         ExtractionService extractionService, CellStyle headerStyle, CellStyle pctStyle) {
        Sheet s = wb.createSheet("AI Extractions & Quality");
        String[] cols = {
                "Case Reference", "Matter Title", "Document File", "Doc Type",
                "Pipeline Status", "Extracted Field", "Extracted Value",
                "Confidence", "Page", "Source Span / Guardrail Note"
        };
        writeHeader(s, 0, cols, headerStyle);

        int rowIdx = 1;
        for (Case c : allCases) {
            for (LegalDocument d : documentService.byCase(c.getId())) {
                List<Extraction> exList = extractionService.byDocument(d.getId());
                if (exList.isEmpty()) {
                    Row r = s.createRow(rowIdx++);
                    r.createCell(0).setCellValue(safe(c.getReference()));
                    r.createCell(1).setCellValue(safe(c.getTitle()));
                    r.createCell(2).setCellValue(safe(d.getFileName()));
                    r.createCell(3).setCellValue(safe(d.getDocType()));
                    r.createCell(4).setCellValue(d.getProcessingStatus() == null ? "" : d.getProcessingStatus().name());
                    r.createCell(5).setCellValue("—");
                    r.createCell(6).setCellValue("—");
                    r.createCell(9).setCellValue(safe(d.getRejectionReason()));
                } else {
                    for (Extraction e : exList) {
                        Row r = s.createRow(rowIdx++);
                        r.createCell(0).setCellValue(safe(c.getReference()));
                        r.createCell(1).setCellValue(safe(c.getTitle()));
                        r.createCell(2).setCellValue(safe(d.getFileName()));
                        r.createCell(3).setCellValue(safe(d.getDocType()));
                        r.createCell(4).setCellValue(d.getProcessingStatus() == null ? "" : d.getProcessingStatus().name());
                        r.createCell(5).setCellValue(safe(e.getFieldName()));
                        r.createCell(6).setCellValue(safe(e.getFieldValue()));
                        Cell confCell = r.createCell(7);
                        confCell.setCellValue(e.getConfidence());
                        confCell.setCellStyle(pctStyle);
                        r.createCell(8).setCellValue(e.getPageNumber());
                        String spanNote = d.getRejectionReason() != null && !d.getRejectionReason().isBlank()
                                ? ("BLOCKED: " + d.getRejectionReason())
                                : safe(e.getSpanText());
                        r.createCell(9).setCellValue(spanNote);
                    }
                }
            }
        }
        autoSize(s, cols.length);
    }

    private void buildBillingSheet(Workbook wb, List<BillingService.BillingEntry> entries, Map<String, Case> caseMap,
                                   CellStyle headerStyle, CellStyle moneyStyle, CellStyle boldMoneyStyle, CellStyle boldTextStyle) {
        Sheet s = wb.createSheet("Billing & 15% VAT");
        String[] cols = {
                "Entry Date", "Case Reference", "Client", "Entry Type", "Description",
                "Hours", "Hourly Rate (ZAR)", "Excl. VAT (ZAR)", "15% SA VAT (ZAR)",
                "Total incl. VAT (ZAR)", "Recorded By"
        };
        writeHeader(s, 0, cols, headerStyle);

        int rowIdx = 1;
        double sumEx = 0.0, sumVat = 0.0, sumIncl = 0.0;
        for (var b : entries) {
            Case c = caseMap.get(b.caseId());
            double ex = b.amount();
            double vat = Math.round(ex * BillingService.SA_VAT_RATE * 100.0) / 100.0;
            double incl = Math.round((ex + vat) * 100.0) / 100.0;
            sumEx += ex;
            sumVat += vat;
            sumIncl += incl;

            Row r = s.createRow(rowIdx++);
            r.createCell(0).setCellValue(b.entryDate() == null ? "" : b.entryDate().toString());
            r.createCell(1).setCellValue(c == null ? b.caseId() : safe(c.getReference()));
            r.createCell(2).setCellValue(c == null ? "" : safe(c.getClientName()));
            r.createCell(3).setCellValue(safe(b.entryType()));
            r.createCell(4).setCellValue(safe(b.description()));
            r.createCell(5).setCellValue(b.hours());
            setMoney(r, 6, b.hourlyRate(), moneyStyle);
            setMoney(r, 7, ex, moneyStyle);
            setMoney(r, 8, vat, moneyStyle);
            setMoney(r, 9, incl, moneyStyle);
            r.createCell(10).setCellValue(safe(b.recordedBy()));
        }

        Row tot = s.createRow(rowIdx + 1);
        Cell lbl = tot.createCell(0);
        lbl.setCellValue("LEDGER TOTALS");
        lbl.setCellStyle(boldTextStyle);
        setMoney(tot, 7, sumEx, boldMoneyStyle);
        setMoney(tot, 8, sumVat, boldMoneyStyle);
        setMoney(tot, 9, sumIncl, boldMoneyStyle);

        autoSize(s, cols.length);
    }

    private void buildTrustAndDeadlinesSheet(Workbook wb, List<TrustAccountingService.TrustEntry> trustEntries,
                                             List<Deadline> deadlines, Map<String, Case> caseMap,
                                             CellStyle headerStyle, CellStyle moneyStyle, CellStyle boldTextStyle) {
        Sheet s = wb.createSheet("Trust & Deadlines");
        String[] trustCols = {
                "Trust Date", "Case Reference", "Reference Code", "Transaction Type",
                "Description", "Amount (ZAR)", "Running Trust Balance (ZAR)", "Recorded By"
        };
        writeHeader(s, 0, trustCols, headerStyle);

        int rowIdx = 1;
        for (var t : trustEntries) {
            Case c = caseMap.get(t.caseId());
            Row r = s.createRow(rowIdx++);
            r.createCell(0).setCellValue(t.txDate() == null ? "" : t.txDate().toString());
            r.createCell(1).setCellValue(c == null ? t.caseId() : safe(c.getReference()));
            r.createCell(2).setCellValue(safe(t.reference()));
            r.createCell(3).setCellValue(safe(t.txType()));
            r.createCell(4).setCellValue(safe(t.description()));
            setMoney(r, 5, t.amount(), moneyStyle);
            setMoney(r, 6, t.balanceAfter(), moneyStyle);
            r.createCell(7).setCellValue(safe(t.recordedBy()));
        }

        rowIdx += 2;
        Row dlTitle = s.createRow(rowIdx++);
        Cell titleCell = dlTitle.createCell(0);
        titleCell.setCellValue("DIARISED DEADLINES & CALENDAR SYNC");
        titleCell.setCellStyle(boldTextStyle);

        String[] dlCols = {"Due Date", "Case Reference", "Deadline Title", "Type", "Reminder (Days)", "Calendar Sync"};
        writeHeader(s, rowIdx++, dlCols, headerStyle);
        for (Deadline d : deadlines) {
            Row r = s.createRow(rowIdx++);
            r.createCell(0).setCellValue(d.getDate() == null ? "" : d.getDate().toString());
            r.createCell(1).setCellValue(safe(d.getCaseReference()));
            r.createCell(2).setCellValue(safe(d.getTitle()));
            r.createCell(3).setCellValue(d.getType() == null ? "" : d.getType().name());
            r.createCell(4).setCellValue(d.getReminderDays());
            r.createCell(5).setCellValue(d.isSynced() ? "SYNCED" : "PENDING");
        }

        autoSize(s, trustCols.length);
    }

    private static void writeHeader(Sheet s, int rowIdx, String[] cols, CellStyle style) {
        Row h = s.createRow(rowIdx);
        for (int i = 0; i < cols.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(style);
        }
    }

    private static void setMoney(Row r, int col, double val, CellStyle style) {
        Cell c = r.createCell(col);
        c.setCellValue(Math.round(val * 100.0) / 100.0);
        c.setCellStyle(style);
    }

    private static void autoSize(Sheet s, int cols) {
        for (int i = 0; i < cols; i++) {
            try {
                s.autoSizeColumn(i);
                int w = s.getColumnWidth(i);
                s.setColumnWidth(i, Math.min(14000, Math.max(3200, w + 700)));
            } catch (Exception ignored) {
                s.setColumnWidth(i, 4800);
            }
        }
    }

    private static CellStyle createHeaderStyle(Workbook wb) {
        CellStyle st = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        st.setFont(f);
        st.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return st;
    }

    private static CellStyle createCurrencyStyle(Workbook wb, boolean bold) {
        CellStyle st = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        st.setDataFormat(fmt.getFormat("\"R\" #,##0.00"));
        if (bold) {
            Font f = wb.createFont();
            f.setBold(true);
            st.setFont(f);
        }
        return st;
    }

    private static CellStyle createPercentStyle(Workbook wb) {
        CellStyle st = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        st.setDataFormat(fmt.getFormat("0%"));
        return st;
    }

    private static CellStyle createBoldStyle(Workbook wb) {
        CellStyle st = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        st.setFont(f);
        return st;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
