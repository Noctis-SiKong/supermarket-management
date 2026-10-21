package nz.ac.aut.pdc.supermarket.app;

import java.io.PrintStream;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import nz.ac.aut.pdc.infrastructure.DataRoot;
import nz.ac.aut.pdc.shared.JsonCodec;

public final class Main {
    private Main() {}
    public static void main(String[] args) { System.exit(run(args,System.out,System.err)); }
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            Path project = null, accountsFile = null; boolean check = false, initialize = false;
            for (int i=0;i<args.length;i++) switch (args[i]) {
                case "--help" -> { out.println("P1: java -jar <jar> [--project-root <repository>] [--accounts <local-hash-json> | --init-accounts] [--check]\nUses real authentication/stock/CUI. No automatic business goods. See README for run and recovery instructions."); return 0; }
                case "--check" -> check = true;
                case "--init-accounts" -> initialize = true;
                case "--project-root" -> { if (++i==args.length) throw new IllegalArgumentException("--project-root needs a directory"); project=Path.of(args[i]); }
                case "--accounts" -> { if (++i==args.length) throw new IllegalArgumentException("--accounts needs a local hash file"); accountsFile=Path.of(args[i]); }
                default -> throw new IllegalArgumentException("Unknown startup option");
            }
            // Exercises the packaged JSON dependency as well as the manifest entry point.
            out.println("P1 JDK " + Runtime.version().feature() + " JSON " + JsonCodec.write(Map.of("schemaVersion",1)));
            if (Runtime.version().feature()!=17) throw new IllegalArgumentException("Run with JDK 17");
            if (project==null) project=locateProject();
            if(initialize && (accountsFile!=null || Files.exists(project.resolve("data/snapshot.json"))))
                throw new IllegalArgumentException("Initialize only a new store; existing credentials are never overwritten");
            var accounts=initialize ? initializeAccounts() : accountsFile==null ? List.<nz.ac.aut.pdc.infrastructure.JsonStore.Credential>of() : loadAccounts(accountsFile);
            // Single production assembly; BIntegration remains the constructor seam for tests.
            BIntegration b=new RepositoryBIntegration(accounts);
            try (Application app=new Application(DataRoot.forProject(project),b,Clock.systemUTC())) {
                if (check) out.println("Storage recovery and registered domain validation passed; no business operation submitted.");
                else b.run(app);
            }
            return 0;
        } catch (Exception e) {
            err.println("STARTUP_BLOCKED: " + e.getClass().getSimpleName() + ": " + e.getMessage()); return 1;
        }
    }
    private static List<nz.ac.aut.pdc.infrastructure.JsonStore.Credential> initializeAccounts() {
        var console=System.console();
        if(console==null) throw new IllegalArgumentException("Interactive console required for --init-accounts; alternatively supply --accounts with hashes");
        var out=new ArrayList<nz.ac.aut.pdc.infrastructure.JsonStore.Credential>();
        Set<String> names=new HashSet<>();
        for(var role:nz.ac.aut.pdc.shared.OperationContext.Role.values()) {
            String username=console.readLine("%s 用户名（exit 放弃）：",role);
            if(username==null || username.isBlank() || username.equalsIgnoreCase("exit") || !names.add(username.trim()))
                throw new IllegalArgumentException("Account setup cancelled or username invalid/duplicated; nothing installed");
            char[] password=console.readPassword("密码（不回显）：");
            try {
                if(password==null || password.length==0)throw new IllegalArgumentException("Password required; nothing installed");
                out.add(new nz.ac.aut.pdc.infrastructure.JsonStore.Credential(UUID.randomUUID().toString(),username.trim(),new nz.ac.aut.pdc.supermarket.auth.Sha256PasswordHasher().hash(password),role));
            } finally {if(password!=null)Arrays.fill(password,'\0');}
        }
        return List.copyOf(out);
    }
    private static List<nz.ac.aut.pdc.infrastructure.JsonStore.Credential> loadAccounts(Path file) throws Exception {
        if (!Files.isRegularFile(file) || Files.size(file)>64_000) throw new IllegalArgumentException("Local account file missing or too large");
        try { return List.of(JsonCodec.read(Files.readString(file),nz.ac.aut.pdc.infrastructure.JsonStore.Credential[].class)); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Invalid local account JSON; expected accountId/username/passwordHash/role, no plaintext passwords"); }
    }
    private static Path locateProject() throws Exception {
        Path start=Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
        for (Path p=Files.isDirectory(start)?start:start.getParent();p!=null;p=p.getParent())
            if (Files.isRegularFile(p.resolve("pom.xml")) && Files.isRegularFile(p.resolve("README.md")) && Files.isDirectory(p.resolve("docs"))) return p;
        throw new IllegalArgumentException("Repository markers not found from executable; specify --project-root");
    }
}
