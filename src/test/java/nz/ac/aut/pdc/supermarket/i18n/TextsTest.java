package nz.ac.aut.pdc.supermarket.i18n;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TextsTest {
    @Test public void missingEntriesHaveOneObservableFallback() {
        var en=Map.of("paired","English %s","englishOnly","Fallback");
        var zh=Map.of("paired","中文 %s","chineseOnly","仅中文");
        Texts texts=new Texts("zh",en,zh);
        assertEquals("Fallback",texts.text("englishOnly"));
        assertEquals("中文 data",texts.text("paired","data"));
        assertEquals(Texts.UNAVAILABLE,texts.text("neither"));
        texts.select("en");
        assertEquals(Texts.UNAVAILABLE,texts.text("chineseOnly"));
        assertEquals(Texts.UNAVAILABLE,texts.text("neither"));
        assertEquals(Texts.UNAVAILABLE,texts.text("paired"));
    }
    @Test public void shippedResourcesHaveMatchingKeysAndFormatArguments() throws Exception {
        Properties en=read("en"),zh=read("zh");
        assertEquals(en.stringPropertyNames(),zh.stringPropertyNames());
        for(String key:en.stringPropertyNames()) {
            assertFalse(key,en.getProperty(key).isBlank()); assertFalse(key,zh.getProperty(key).isBlank());
            assertEquals(key,parameters(en.getProperty(key)),parameters(zh.getProperty(key)));
            assertFalse(key,Pattern.compile("[\\p{IsHan}]").matcher(en.getProperty(key)).find());
        }
    }
    @Test public void dataValuesAreNeverUsedAsResourceKeys() {
        var projection=Map.of("name","中文商品","unit","SALE","reason","用户原文");
        for(String lang:List.of("en","zh")) {
            String rendered=Texts.data(projection).render(new Texts(lang));
            assertTrue(rendered,rendered.contains("中文商品")); assertTrue(rendered,rendered.contains("用户原文"));
            assertTrue(rendered,rendered.contains("SALE")); assertFalse(rendered,rendered.contains("(SALE)"));
            assertFalse(rendered,rendered.contains("{")); assertFalse(rendered,rendered.contains(Texts.UNAVAILABLE));
        }
    }
    private Properties read(String lang) throws Exception {
        Properties properties=new Properties();
        try(var reader=new InputStreamReader(Texts.class.getResourceAsStream(lang+".properties"),StandardCharsets.UTF_8)) {properties.load(reader);}
        return properties;
    }
    private List<String> parameters(String text) {
        var result=new ArrayList<String>();var matcher=Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]").matcher(text);
        while(matcher.find())result.add(matcher.group());return result;
    }
}
