package nz.ac.aut.pdc.supermarket.app;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import nz.ac.aut.pdc.infrastructure.DataRoot;
import nz.ac.aut.pdc.shared.*;
import nz.ac.aut.pdc.supermarket.auth.AuthResult;

/** TEST PROCESS ONLY. Actual abrupt JVM termination after durable trace and before caller response. */
public final class CrashAfterCommitProbe {
    public static void main(String[] args) throws Exception {
        var b=new RepositoryBIntegration(List.of());
        var app=new Application(DataRoot.forProject(Path.of(args[0])),b,Clock.systemUTC());
        var login=(AuthResult.Success)b.auth().login("fictional-clerk",System.getenv("P1_TEST_SECRET").toCharArray());
        var context=new OperationContext(login.session(),args[3],AftersaleReadRequest.OPERATION,args[2]);
        var result=app.originalSales().readForAftersale(app.submissions(),context,new AftersaleReadRequest(args[1],args[2],List.of()));
        if(!result.hasValue()) {System.err.println(result.code());Runtime.getRuntime().halt(22);}
        Runtime.getRuntime().halt(23); // Deliberately no close(), shutdown hooks or external data response.
    }
}
