package nz.ac.aut.pdc.shared;

public record DocumentLine(String lineId, String documentId, String lineKind, String barcode, int quantity) {}
