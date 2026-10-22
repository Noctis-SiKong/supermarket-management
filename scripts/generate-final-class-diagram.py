"""Inventory compiled production types, generate complete UML and render offline SVG.
Requires JDK 17 and an existing dependency JAR; rendering also uses Python Playwright,
local Mermaid JS and an existing Edge browser. Does not initialize business classes or runtime data.
"""
from pathlib import Path
import argparse, base64, hashlib, json, os, re, subprocess, tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/design/final"
INSPECTOR = r'''
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
public class InspectProductionTypes {
    static String visibility(int m) {return Modifier.isPublic(m)?"+":Modifier.isProtected(m)?"#":Modifier.isPrivate(m)?"-":"~";}
    static List<String> parameters(Class<?>[] types) {return Arrays.stream(types).map(Class::getTypeName).toList();}
    public static void main(String[] args) throws Exception {
        Path classes=Path.of(args[0]);List<Object> all=new ArrayList<>();
        List<Path> files;
        try(var paths=Files.walk(classes)) {files=paths.filter(p -> p.toString().endsWith(".class")).sorted().toList();}
        for(Path file:files) {
            String name=classes.relativize(file).toString().replace('\\','.').replace('/','.').replaceAll("\\.class$","");
            if(!name.startsWith("nz.ac.aut.pdc."))continue;
            Class<?> c=Class.forName(name,false,InspectProductionTypes.class.getClassLoader());
            if(c.isSynthetic() || c.isAnonymousClass() || c.isLocalClass())continue;
            Map<String,Object> t=new LinkedHashMap<>();t.put("name",name);t.put("package",c.getPackageName());
            t.put("kind",c.isInterface()?"interface":c.isEnum()?"enum":c.isRecord()?"record":Modifier.isAbstract(c.getModifiers())?"abstract":"class");
            t.put("superclass",c.getSuperclass()==null?null:c.getSuperclass().getName());
            t.put("interfaces",Arrays.stream(c.getInterfaces()).map(Class::getName).toList());
            List<Object> fields=new ArrayList<>(),methods=new ArrayList<>();
            for(Field f:c.getDeclaredFields())if(!f.isSynthetic())fields.add(Map.of("name",f.getName(),"type",f.getType().getTypeName(),"genericType",f.getGenericType().getTypeName(),"visibility",visibility(f.getModifiers())));
            Set<String> components=new HashSet<>();if(c.isRecord())for(var r:c.getRecordComponents())components.add(r.getName());
            for(Constructor<?> m:c.getDeclaredConstructors())if(!m.isSynthetic())methods.add(Map.of("name",c.getSimpleName(),"parameters",parameters(m.getParameterTypes()),"type","","visibility",visibility(m.getModifiers())));
            for(Method m:c.getDeclaredMethods()) {
                if(m.isSynthetic() || m.isBridge() || !(Modifier.isPublic(m.getModifiers()) || Modifier.isProtected(m.getModifiers())))continue;
                if(c.isRecord() && (components.contains(m.getName()) || Set.of("equals","hashCode","toString").contains(m.getName())))continue;
                if(c.isEnum() && Set.of("values","valueOf").contains(m.getName()))continue;
                methods.add(Map.of("name",m.getName(),"parameters",parameters(m.getParameterTypes()),"type",m.getReturnType().getTypeName(),"visibility",visibility(m.getModifiers())));
            }
            t.put("fields",fields);t.put("methods",methods);all.add(t);
        }
        System.out.print(new ObjectMapper().writeValueAsString(all));
    }
}'''

def command(args, **kwargs):
    return subprocess.run([str(a) for a in args], check=True, capture_output=True,
                          creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0, **kwargs)

def alias(name):
    return "T" + hashlib.sha256(name.encode()).hexdigest()[:12]

def short(name):
    return re.sub(r"(?:[a-z_][\w]*\.)+", "", name).replace("$", ".")

CALLS = [
    ("supermarket.app.Main", "supermarket.app.Application", "assembles"),
    ("supermarket.app.Main", "supermarket.app.RepositoryBIntegration", "constructs"),
    ("supermarket.app.RepositoryBIntegration", "supermarket.aftersale.BCommitValidators", "validators"),
    ("supermarket.app.RepositoryBIntegration", "supermarket.cui.CuiApp", "run"),
    ("supermarket.app.RepositoryBIntegration", "supermarket.cui.SharedOperations", "adapts"),
    ("supermarket.aftersale.BCommitValidators", "supermarket.stock.StockOperationValidator", "registers"),
    ("supermarket.aftersale.BCommitValidators", "supermarket.reversal.ReversalCommitValidator", "registers"),
    ("supermarket.aftersale.BCommitValidators", "supermarket.aftersale.AftersaleCommitValidator", "registers"),
    ("supermarket.aftersale.BCommitValidators", "supermarket.aftersale.AftersaleDraftDeletionValidator", "registers"),
    ("infrastructure.JsonStore", "shared.SubmissionPort$CommitValidator", "validate under lock"),
    ("infrastructure.AbstractReconstructingCommitValidator", "infrastructure.SubmissionTime", "assign"),
    ("infrastructure.AbstractReconstructingCommitValidator", "shared.CandidateEquality", "same"),
    ("supermarket.stock.StockOperationValidator", "supermarket.stock.StockBusiness", "prepare"),
    ("supermarket.reversal.ReversalCommitValidator", "supermarket.reversal.EffectiveDependencies", "blocker"),
    ("supermarket.reversal.ReversalCommitValidator", "supermarket.reversal.ReversalChangeSetAssembler", "assemble"),
    ("supermarket.reversal.ReversalCommitValidator", "supermarket.stock.SharedStockAdapter", "review"),
    ("supermarket.aftersale.AftersaleCommitValidator", "supermarket.aftersale.AftersaleEngine", "reconstructs separately"),
    ("supermarket.cui.SharedOperations", "infrastructure.JsonStore", "open lookup submit"),
]

def mermaid(types, include_fields=True):
    names={t["name"] for t in types};lines=["classDiagram", "direction TB"]
    for t in types:
        label=t["name"].removeprefix("nz.ac.aut.pdc.").replace("$", ".")
        lines.append(f'class {alias(t["name"])}["{label}"] {{')
        if t["kind"] != "class": lines.append("  <<"+t["kind"]+">>")
        if include_fields:
            for f in sorted(t["fields"],key=lambda x:x["name"]):
                lines.append(f'  {f["visibility"]}{short(f["type"])} {f["name"]}')
        for m in sorted(t["methods"],key=lambda x:(x["name"],str(x["parameters"]))):
            lines.append(f'  {m["visibility"]}{m["name"]}({", ".join(short(v) for v in m["parameters"])}) {short(m["type"])}')
        lines.append("}")
    edges=set()
    for t in types:
        if t["superclass"] in names: edges.add((t["superclass"], t["name"], "<|--", "inherits"))
        for parent in t["interfaces"]:
            if parent in names:
                edges.add((parent,t["name"],"<|--" if t["kind"]=="interface" else "<|..",
                    "extends" if t["kind"]=="interface" else "implements"))
        for f in t["fields"]:
            for target in re.findall(r"nz\.ac\.aut\.pdc\.[\w.$]+",f["genericType"]):
                if target in names and target != t["name"]: edges.add((t["name"],target,"-->","field type"))
    for a,b,label in CALLS:
        a,b="nz.ac.aut.pdc."+a,"nz.ac.aut.pdc."+b
        if a in names and b in names: edges.add((a,b,"..>",label))
    for a,b,style,label in sorted(edges): lines.append(f'{alias(a)} {style} {alias(b)} : {label}')
    return "\n".join(line.rstrip() for line in lines)+"\n",len(edges)

def render(source, output, library, edge):
    encoded=base64.b64encode(source.encode()).decode()
    page=f'''<!doctype html><meta charset="utf-8"><title>WAIT</title>
<body><div id="diagram"></div><textarea id="rendered"></textarea><script>window.onerror=function(e){{document.title='ERR';document.getElementById('rendered').textContent=String(e);}};</script>
<script src="{library.resolve().as_uri()}"></script>
<script>const mermaid=window.mermaid || __esbuild_esm_mermaid_nm.mermaid.default;
mermaid.initialize({{startOnLoad:false,securityLevel:'strict',maxTextSize:1000000,maxEdges:10000,
theme:'neutral',themeVariables:{{fontFamily:'Segoe UI,Arial,sans-serif',fontSize:'16px'}},class:{{useMaxWidth:false}}}});
const source=new TextDecoder().decode(Uint8Array.from(atob('{encoded}'),c=>c.charCodeAt(0)));
mermaid.render('finalDiagram',source).then(r=>{{document.getElementById('rendered').textContent=r.svg;document.title='OK';}})
.catch(e=>{{document.getElementById('rendered').textContent=String(e);document.title='ERR';}});</script>'''
    from playwright.sync_api import sync_playwright
    with tempfile.TemporaryDirectory(prefix="p1-diagram-") as tmp:
        path=Path(tmp)/"render.html";path.write_text(page,encoding="utf-8")
        with sync_playwright() as browser_tools:
            browser=browser_tools.chromium.launch(executable_path=str(edge),headless=True,args=['--allow-file-access-from-files'])
            try:
                tab=browser.new_page();tab.goto(path.as_uri())
                tab.wait_for_function("['OK','ERR'].includes(document.title)",timeout=90000)
                svg=tab.locator('#rendered').text_content()
                if tab.title()!='OK': raise RuntimeError(svg)
                parsed=ET.fromstring(svg)
                # Keep native readable dimensions instead of squeezing the complete system into a viewport.
                width,height=parsed.attrib['viewBox'].split()[2:]
                root=re.search(r'<svg\b[^>]*>',svg).group()
                sized=root
                for key,value in [('width',width),('height',height),('style','max-width:none')]:
                    if re.search(r'\s'+key+r'="[^"]*"',sized):
                        sized=re.sub(r'\s'+key+r'="[^"]*"',f' {key}="{value}"',sized,count=1)
                    else: sized=sized[:-1]+f' {key}="{value}">'
                svg=svg.replace(root,sized,1);ET.fromstring(svg)
                output.write_text(svg,encoding="utf-8")
            finally: browser.close()

def main():
    parser=argparse.ArgumentParser();parser.add_argument("--java-home",default=os.environ.get("JAVA_HOME"));parser.add_argument("--mermaid-js",type=Path)
    parser.add_argument("--edge",type=Path,default=Path(os.environ.get("PROGRAMFILES(X86)",""))/"Microsoft/Edge/Application/msedge.exe")
    args=parser.parse_args();jdk=Path(args.java_home);classes=ROOT/"target/classes";jar=ROOT/"target/supermarket-management-0.1.0-SNAPSHOT-jar-with-dependencies.jar"
    with tempfile.TemporaryDirectory(prefix="p1-types-") as tmp:
        tmp=Path(tmp);java=tmp/"InspectProductionTypes.java";java.write_text(INSPECTOR,encoding="utf-8")
        cp=os.pathsep.join([str(classes),str(jar)]);command([jdk/"bin/javac.exe","-encoding","UTF-8","-cp",cp,"-d",tmp,java])
        output=command([jdk/"bin/java.exe","-cp",str(tmp)+os.pathsep+cp,"InspectProductionTypes",classes]);types=json.loads(output.stdout)
    types.sort(key=lambda x:x["name"])
    for t in types:
        t["source"]="src/main/java/"+t["name"].split("$")[0].replace(".","/")+".java"
        assert (ROOT/t["source"]).is_file(),t["name"]
    assert not any(t["name"].endswith((".SubmissionGateway",".ReversalService",".FictionalDependencies")) for t in types)
    sources={str(p.relative_to(ROOT)).replace("\\","/"):hashlib.sha256(p.read_bytes().replace(b'\r\n',b'\n')).hexdigest() for p in sorted((ROOT/"src/main/java").rglob("*.java"))}
    assert {t["source"] for t in types} == set(sources),"Every source file must be represented"
    OUT.mkdir(parents=True,exist_ok=True);full,edges=mermaid(types)
    focus_suffixes={"shared.SubmissionPort$CommitValidator","infrastructure.AbstractReconstructingCommitValidator","supermarket.stock.StockOperationValidator",
        "supermarket.reversal.ReversalCommitValidator","infrastructure.JsonStore","supermarket.aftersale.BCommitValidators","infrastructure.SubmissionTime","shared.CandidateEquality"}
    focus,_=mermaid([t for t in types if t["name"].removeprefix("nz.ac.aut.pdc.") in focus_suffixes],False)
    (OUT/"system.mmd").write_text(full,encoding="utf-8");(OUT/"validator-template.mmd").write_text(focus,encoding="utf-8")
    inventory={"sourceFiles":len(sources),"namedTypes":len(types),"relationships":edges,"types":types,"sourceSha256":sources,
        "sourceHashNormalization":"Source bytes with CRLF normalized to LF; independent of Git checkout line endings.",
        "coverage":"All named production types and declared fields; constructors/public/protected methods. Generated record accessors/Object methods and enum values/valueOf omitted; erased short type names in UML, fully qualified types here. JDK/third-party and synthetic/anonymous/local classes excluded. Field-type associations and selected source-verified calls, not a complete call graph."}
    (OUT/"inventory.json").write_text(json.dumps(inventory,indent=2),encoding="utf-8")
    if args.mermaid_js:
        render(full,OUT/"system.svg",args.mermaid_js,args.edge);render(focus,OUT/"validator-template.svg",args.mermaid_js,args.edge)
    print(json.dumps({k:inventory[k] for k in ["sourceFiles","namedTypes","relationships"]}))

if __name__ == "__main__": main()
