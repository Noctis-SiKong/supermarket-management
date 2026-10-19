package nz.ac.aut.pdc.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Build infrastructure only: no inventory or other business behavior. */
public class FrameworkSmokeTest {
    @Test
    public void runsOnJdk17() {
        assertEquals(17, Runtime.version().feature());
    }

    @Test
    public void jacksonRoundTripsChineseJsonAsUtf8() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> input = Map.of("message", "框架验证");
        byte[] json = mapper.writeValueAsBytes(input);
        assertTrue(new String(json, StandardCharsets.UTF_8).contains("框架验证"));
        assertEquals(input, mapper.readValue(json, Map.class));
    }
}
