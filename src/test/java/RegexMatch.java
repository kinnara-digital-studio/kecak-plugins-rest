import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class RegexMatch {
    @Test
    public void test1() {
        Map<String, String> map = new HashMap<>();
        map.put("headers[0][key]", "Referer");
        map.put("headers[1][key]", "Authorization");
        map.put("headers[1][value]", "Basic YWRtaW46UEBzc3dvcmQxMjM=");
        map.put("headers[0][value]", "http://localhost");

        getUnformattedGridProperty(map, "headers")
                .forEach((k, v) -> System.out.println(k + "->" + v));
    }


    private Map<String, Object> getUnformattedGridProperty(Map<String, String> properties, String propertyName) {
        Map<String, Object> result = new HashMap<>();

        int i = 0;
        String key;
        String value;
        do {
            key = properties.get(propertyName + "[" + i + "][key]");
            value = properties.get(propertyName + "[" + i + "][value]");

            if(key != null && value != null) {
                result.put(key, value);
            }
            i++;
        } while(key != null);

        return result;
    }
}
