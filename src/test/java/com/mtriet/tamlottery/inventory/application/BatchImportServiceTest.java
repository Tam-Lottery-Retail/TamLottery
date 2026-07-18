package com.mtriet.tamlottery.inventory.application;

import com.mtriet.tamlottery.common.exception.BusinessException;
import com.mtriet.tamlottery.inventory.api.InventoryDtos;
import com.mtriet.tamlottery.masterdata.domain.LotteryRegion;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchImportServiceTest {

    private final BatchImportService service = new BatchImportService();

    @Test
    void previewsSemicolonCsvWithVietnameseHeaders() {
        String csv = """
                mã đại lý;mã phiếu nhận;ngày bán;thời điểm nhận;đơn vị phát hành;mã tỉnh;miền;ngày quay;hạn trả vé;số lượng;giá nhập;giá bán
                DL001;PN-001;18/7/2026;18/7/2026 08:00;XSKT TP.HCM;HCM;Miền Nam;18/7/2026;18/7/2026 15:30;100;9000;10000
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "lo-ve.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        InventoryDtos.BatchImportPreviewResponse preview = service.preview(file);

        assertThat(preview.agencyCode()).isEqualTo("DL001");
        assertThat(preview.receiptCode()).isEqualTo("PN-001");
        assertThat(preview.businessDate()).isEqualTo(LocalDate.of(2026, 7, 18));
        assertThat(preview.receivedAt()).isEqualTo(Instant.parse("2026-07-18T01:00:00Z"));
        assertThat(preview.lines()).singleElement().satisfies(line -> {
            assertThat(line.issuerName()).isEqualTo("XSKT TP.HCM");
            assertThat(line.provinceCode()).isEqualTo("HCM");
            assertThat(line.region()).isEqualTo(LotteryRegion.SOUTH);
            assertThat(line.quantityReceived()).isEqualTo(100);
            assertThat(line.unitSalePrice()).isEqualTo(10_000);
        });
    }

    @Test
    void previewsFirstSheetFromXlsx() throws Exception {
        byte[] content;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Lo ve");
            var headers = sheet.createRow(0);
            String[] names = { "agencyCode", "receiptCode", "businessDate", "receivedAt", "issuerName",
                    "provinceCode", "region", "drawDate", "returnCutoffAt", "quantityReceived", "unitCost", "unitSalePrice" };
            for (int index = 0; index < names.length; index++) headers.createCell(index).setCellValue(names[index]);
            var row = sheet.createRow(1);
            String[] values = { "DL001", "PN-002", "2026-07-18", "2026-07-18T08:00:00+07:00", "XSKT Long An",
                    "LA", "SOUTH", "2026-07-18", "2026-07-18T15:30:00+07:00", "120", "9000", "10000" };
            for (int index = 0; index < values.length; index++) row.createCell(index).setCellValue(values[index]);
            workbook.write(output);
            content = output.toByteArray();
        }

        InventoryDtos.BatchImportPreviewResponse preview = service.preview(new MockMultipartFile(
                "file", "lo-ve.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content));

        assertThat(preview.lines()).singleElement().satisfies(line -> {
            assertThat(line.provinceCode()).isEqualTo("LA");
            assertThat(line.quantityReceived()).isEqualTo(120);
        });
        assertThat(preview.warnings()).isEmpty();
    }

    @Test
    void rejectsUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile("file", "lo-ve.pdf", "application/pdf", new byte[] { 1 });

        assertThatThrownBy(() -> service.preview(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(".csv");
    }
}
