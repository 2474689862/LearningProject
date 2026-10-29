package com.aistudy.search;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认档检索：Bing RSS 为主、搜狗网页为兜底，只取标题与摘要。
 * <p>参考项目里的全文抓取、BM25 与 Jina 重排在这里全部省略，避免引入额外依赖与长耗时；
 * 默认档定位为「弱证据」：拿到多少用多少，失败静默返回空列表。</p>
 */
public class DefaultSearchProvider implements SearchProvider {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final int LIMIT = 8;
    private static final int TIMEOUT_MS = 4000;

    private static final Pattern SOGOU_RESULT =
            Pattern.compile("(?is)<h3[^>]*>\\s*<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>");
    private static final Pattern CHARSET =
            Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE);

    @Override
    public String name() {
        return "搜狗 + Bing";
    }

    @Override
    public List<SearchHit> search(String query, String apiKey) {
        // 中文题干在搜狗上的相关性明显更好，Bing RSS 只按首个词匹配，仅作兜底
        List<SearchHit> hits = new ArrayList<>(sogou(query));
        if (hits.size() < LIMIT) {
            hits.addAll(bing(query));
        }
        return dedupe(hits);
    }

    private List<SearchHit> bing(String query) {
        List<SearchHit> hits = new ArrayList<>();
        String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
        for (String host : new String[]{"cn.bing.com", "www.bing.com"}) {
            String url = "https://" + host + "/search?q=" + q
                    + "&format=rss&count=8&setlang=zh-CN&mkt=zh-CN";
            try {
                HttpResponse<byte[]> res = get(url);
                if (res.statusCode() != 200) {
                    continue;
                }
                String body = decode(res);
                if (!body.contains("<item")) {
                    continue;
                }
                hits.addAll(parseBingRss(body));
                if (!hits.isEmpty()) {
                    return hits;
                }
            } catch (Exception ignored) {
                // 单个引擎失败不影响整体，交给下一个引擎兜底
            }
        }
        return hits;
    }

    private List<SearchHit> parseBingRss(String xml) {
        List<SearchHit> hits = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength() && hits.size() < LIMIT; i++) {
                Node item = items.item(i);
                if (item.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                String title = plain(childText(item, "title"));
                String link = childText(item, "link").trim();
                String desc = plain(childText(item, "description"));
                if (title.isEmpty() || !validUrl(link)) {
                    continue;
                }
                hits.add(new SearchHit(title, link, cut(desc, 160), "", "Bing"));
            }
        } catch (Exception ignored) {
            // 返回内容不是合法 RSS 时按无结果处理
        }
        return hits;
    }

    private List<SearchHit> sogou(String query) {
        List<SearchHit> hits = new ArrayList<>();
        String url = "https://www.sogou.com/web?query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
        try {
            HttpResponse<byte[]> res = get(url);
            if (res.statusCode() != 200) {
                return hits;
            }
            String html = decode(res);
            Matcher m = SOGOU_RESULT.matcher(html);
            while (m.find() && hits.size() < LIMIT) {
                String href = m.group(1).trim();
                String title = plain(m.group(2));
                if (title.isEmpty() || href.isEmpty()) {
                    continue;
                }
                if (href.startsWith("/")) {
                    href = "https://www.sogou.com" + href;
                }
                href = href.replace("&amp;", "&");
                if (!validUrl(href)) {
                    continue;
                }
                int end = Math.min(html.length(), m.end() + 1500);
                String snippet = cut(plain(html.substring(m.end(), end)), 140);
                hits.add(new SearchHit(title, href, snippet, "", "搜狗"));
            }
        } catch (Exception ignored) {
            // 搜狗反爬或超时都按无结果处理
        }
        return hits;
    }

    private HttpResponse<byte[]> get(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(2000))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(TIMEOUT_MS))
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private static List<SearchHit> dedupe(List<SearchHit> hits) {
        List<SearchHit> out = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (SearchHit h : hits) {
            String key = h.url.replaceAll("/+$", "").toLowerCase();
            String titleKey = h.title.replaceAll("[\\s\\W_]+", "");
            if (keys.contains(key) || keys.contains(titleKey)) {
                continue;
            }
            keys.add(key);
            keys.add(titleKey);
            out.add(h);
            if (out.size() >= LIMIT) {
                break;
            }
        }
        return out;
    }

    private static boolean validUrl(String url) {
        if (url == null || !url.startsWith("http")) {
            return false;
        }
        String lower = url.toLowerCase();
        return !lower.contains("bing.com") && !lower.contains("go.microsoft.com");
    }

    private static String childText(Node parent, String tag) {
        NodeList list = parent.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (tag.equalsIgnoreCase(n.getNodeName())) {
                return n.getTextContent() == null ? "" : n.getTextContent().trim();
            }
        }
        return "";
    }

    private static String decode(HttpResponse<byte[]> res) {
        byte[] bytes = res.body() == null ? new byte[0] : res.body();
        String charset = "UTF-8";
        String contentType = res.headers().firstValue("content-type").orElse("");
        Matcher m = CHARSET.matcher(contentType);
        if (m.find()) {
            charset = m.group(1);
        }
        try {
            return new String(bytes, Charset.forName(charset));
        } catch (Exception e) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /** 去标签 + 解常见实体，用于展示的纯文本 */
    static String plain(String html) {
        if (html == null) {
            return "";
        }
        String s = html
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?i)</?em>", "")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
        return s.replaceAll("\\s+", " ").trim();
    }

    static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
