package nz.ac.aut.pdc.supermarket.aftersale;

/** B-owned operation names for drafts and read traces.
 *
 * B 负责的售后草稿/读取追溯操作名。 */
public final class AftersaleDraftOperations {

    public static final String CREATE = "AFTERSALE_DRAFT_CREATE";
    public static final String READ = "AFTERSALE_READ";
    public static final String CANCEL = "AFTERSALE_DRAFT_CANCEL";

    private AftersaleDraftOperations() {
    }
}
