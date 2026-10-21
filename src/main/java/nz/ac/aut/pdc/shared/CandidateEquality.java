package nz.ac.aut.pdc.shared;

import java.util.*;
/** Exact business effects after applying the explicit server time matrix. No time fields are ignored. */
public final class CandidateEquality {
    private CandidateEquality() {}
    public static boolean same(ChangeSet a,ChangeSet b) {
        return records(a).equals(records(b)) && patches(a).equals(patches(b))
            && a.removals().size()==b.removals().size() && new HashSet<>(a.removals()).equals(new HashSet<>(b.removals()));
    }
    private static Map<String,Object> records(ChangeSet c) {
        Map<String,Object> out=new TreeMap<>();
        for (var r:c.inserts()) if(out.put(r.key(),List.of(r.version(),r.data()))!=null) throw new IllegalArgumentException("EVENT_CONFLICT");
        return out;
    }
    private static Map<String,Object> patches(ChangeSet c) {
        Map<String,Object> out=new TreeMap<>();
        for (var p:c.patches()) if(out.put(p.key(),List.of(p.expectedVersion(),JsonCodec.tree(p.changedFields())))!=null) throw new IllegalArgumentException("EVENT_CONFLICT");
        return out;
    }
}
