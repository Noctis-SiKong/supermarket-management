package nz.ac.aut.pdc.supermarket.cui;

import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import nz.ac.aut.pdc.infrastructure.*;
import nz.ac.aut.pdc.shared.IdClock;
import nz.ac.aut.pdc.supermarket.app.*;
import nz.ac.aut.pdc.supermarket.auth.AuthService;

/** Compatibility facade delegating to the one Application assembly. */
public final class BApplication implements AutoCloseable {
    private final Application app;
    private final RepositoryBIntegration integration;
    public BApplication(Path root,List<JsonStore.Credential> credentials) throws IOException {
        this(root,credentials,new IdClock(Clock.systemUTC()));
    }
    public BApplication(Path root,List<JsonStore.Credential> credentials,IdClock ids) throws IOException {
        integration=new RepositoryBIntegration(credentials);
        try { app=new Application(DataRoot.forProject(root),integration,new Clock() {
            public ZoneId getZone(){return ZoneOffset.UTC;}
            public Clock withZone(ZoneId zone){return this;}
            public Instant instant(){return ids.now();}
        }); } catch(IOException e){throw e;} catch(Exception e){throw new IOException("Assembly failed",e);}
    }
    public AuthService authService(){return integration.auth();}
    public JsonStore store(){return (JsonStore)app.submissions();}
    public Operations operations(){return integration.cuiOperations(app);}
    public void close() throws IOException {try{app.close();}catch(IOException e){throw e;}catch(Exception e){throw new IOException(e);}}
}
