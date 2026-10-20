package nz.ac.aut.pdc.shared;

import java.math.BigDecimal;
public record MoneyEntry(String moneyEntryId, String documentId, BigDecimal amountSigned,
                         String requestKey, String reverseOfId) {}
