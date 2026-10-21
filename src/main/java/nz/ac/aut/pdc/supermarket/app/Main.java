package nz.ac.aut.pdc.supermarket.app;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import nz.ac.aut.pdc.supermarket.i18n.Texts;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import nz.ac.aut.pdc.infrastructure.DataRoot;
import nz.ac.aut.pdc.shared.JsonCodec;

public final class Main {
    private Main() {}
    public static void main(String[] args) {
        // Windows console encoding can override file.encoding; keep the CUI streams explicitly UTF-8.
        System.exit(run(args,new PrintStream(System.out,true,StandardCharsets.UTF_8),new PrintStream(System.err,true,StandardCharsets.UTF_8)));
    }
    public static int run(String[] args,PrintStream out,PrintStream err) {
        Texts texts = new Texts("en");
        try {
            // Language applies to help too; do not reinterpret values of existing options.
            for (int i=0;i<args.length;i++) {
                if (args[i].equals("--lang")) {
                    if (++i==args.length || (!args[i].equals("en") && !args[i].equals("zh"))) {
                        err.println(new Texts("en").text("language.invalid")); return 1;
                    }
                    texts.select(args[i]);
                } else if ((args[i].equals("--project-root") || args[i].equals("--accounts")) && i+1<args.length) i++;
            }
            Path project = null, accountsFile = null; boolean check = false, initialize = false;
            for (int i=0;i<args.length;i++) switch (args[i]) {
                case "--lang" -> i++;
                case "--help" -> { out.println(texts.text("start.help")); return 0; }
                case "--check" -> check = true;
                case "--init-accounts" -> initialize = true;
                case "--project-root" -> { if (++i==args.length) throw new IllegalArgumentException("cli.project"); project=Path.of(args[i]); }
                case "--accounts" -> { if (++i==args.length) throw new IllegalArgumentException("cli.accounts"); accountsFile=Path.of(args[i]); }
                default -> throw new IllegalArgumentException("cli.unknown");
            }
            // Exercises the packaged JSON dependency as well as the manifest entry point.
            out.println(texts.text("start.banner", Runtime.version().feature(), JsonCodec.tree(JsonCodec.write(Map.of("schemaVersion",1))).path("schemaVersion").asInt()));
            if (Runtime.version().feature()!=17) throw new IllegalArgumentException("cli.jdk");
            if (project==null) project=locateProject();
            if(initialize && (accountsFile!=null || Files.exists(project.resolve("data/snapshot.json"))))
                throw new IllegalArgumentException("account.existing");
            var accounts=initialize ? initializeAccounts(texts,out) : accountsFile==null ? List.<nz.ac.aut.pdc.infrastructure.JsonStore.Credential>of() : loadAccounts(accountsFile);
            // Single production assembly; BIntegration remains the constructor seam for tests.
            BIntegration b=new RepositoryBIntegration(accounts,texts,out,System.in);
            try (Application app=new Application(DataRoot.forProject(project),b,Clock.systemUTC())) {
                if (check) out.println(texts.text("start.check"));
                else b.run(app);
            }
            return 0;
        } catch (Exception e) {
            String key=e.getMessage();
            String explanation;
            if(key!=null && (key.startsWith("cli.") || key.startsWith("account."))) explanation=texts.text(key);
            else if(key!=null && key.contains("RECOVERY_WRITE_BLOCKED")) explanation=texts.error("RECOVERY_WRITE_BLOCKED");
            else if(key!=null && key.contains("writer already active")) explanation=texts.error("WRITE_BLOCKED");
            else explanation=texts.text("start.unavailable");
            err.println(texts.text("start.blocked",explanation)); return 1;
        }
    }
    private static List<nz.ac.aut.pdc.infrastructure.JsonStore.Credential> initializeAccounts(Texts texts,PrintStream output) {
        var console=System.console();
        if(console==null) throw new IllegalArgumentException("account.console");
        var out=new ArrayList<nz.ac.aut.pdc.infrastructure.JsonStore.Credential>();
        Set<String> names=new HashSet<>();
        for(var role:nz.ac.aut.pdc.shared.OperationContext.Role.values()) {
            output.print(texts.text("account.username",texts.label(role.name())));output.flush();
            String username=console.readLine();
            if(username==null || username.isBlank() || username.equalsIgnoreCase("exit") || !names.add(username.trim()))
                throw new IllegalArgumentException("account.cancelled");
            output.print(texts.text("account.password"));output.flush();
            char[] password=console.readPassword();
            try {
                if(password==null || password.length==0)throw new IllegalArgumentException("account.passwordRequired");
                out.add(new nz.ac.aut.pdc.infrastructure.JsonStore.Credential(UUID.randomUUID().toString(),username.trim(),new nz.ac.aut.pdc.supermarket.auth.Sha256PasswordHasher().hash(password),role));
            } finally {if(password!=null)Arrays.fill(password,'\0');}
        }
        return List.copyOf(out);
    }
    private static List<nz.ac.aut.pdc.infrastructure.JsonStore.Credential> loadAccounts(Path file) throws Exception {
        if (!Files.isRegularFile(file) || Files.size(file)>64_000) throw new IllegalArgumentException("account.file");
        try { return List.of(JsonCodec.read(Files.readString(file),nz.ac.aut.pdc.infrastructure.JsonStore.Credential[].class)); }
        catch (RuntimeException e) { throw new IllegalArgumentException("account.file"); }
    }
    private static Path locateProject() throws Exception {
        Path start=Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
        for (Path p=Files.isDirectory(start)?start:start.getParent();p!=null;p=p.getParent())
            if (Files.isRegularFile(p.resolve("pom.xml")) && Files.isRegularFile(p.resolve("README.md")) && Files.isDirectory(p.resolve("docs"))) return p;
        throw new IllegalArgumentException("cli.root");
    }
}
