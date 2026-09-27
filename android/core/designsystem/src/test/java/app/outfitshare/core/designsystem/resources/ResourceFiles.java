package app.outfitshare.core.designsystem.resources;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Чтение XML-ресурсов модуля в JVM-тестах (рабочая директория тестов — каталог модуля). */
final class ResourceFiles {

  static final File RES = new File("src/main/res");

  private ResourceFiles() {}

  static Document parse(File file) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      return factory.newDocumentBuilder().parse(file);
    } catch (ParserConfigurationException | SAXException | IOException e) {
      throw new AssertionError("Не удалось разобрать " + file, e);
    }
  }

  static List<File> files(String dir, String prefix) {
    File[] all =
        new File(RES, dir).listFiles((d, name) -> name.startsWith(prefix) && name.endsWith(".xml"));
    List<File> out = new ArrayList<>();
    if (all != null) {
      for (File f : all) {
        out.add(f);
      }
    }
    return out;
  }

  /** name → значение для элементов {@code tag} (color, dimen, item) из файла values. */
  static Map<String, String> values(String relativePath, String tag) {
    Map<String, String> out = new LinkedHashMap<>();
    NodeList nodes = parse(new File(RES, relativePath)).getElementsByTagName(tag);
    for (int i = 0; i < nodes.getLength(); i++) {
      Element e = (Element) nodes.item(i);
      out.put(e.getAttribute("name"), e.getTextContent().trim());
    }
    return out;
  }

  /** Все стили из values/*.xml: имя → (атрибут → значение), с учётом наследования. */
  static Map<String, Map<String, String>> styles() {
    Map<String, Element> raw = new HashMap<>();
    for (File f : files("values", "")) {
      NodeList nodes = parse(f).getElementsByTagName("style");
      for (int i = 0; i < nodes.getLength(); i++) {
        Element e = (Element) nodes.item(i);
        raw.put(e.getAttribute("name"), e);
      }
    }
    Map<String, Map<String, String>> resolved = new HashMap<>();
    for (String name : raw.keySet()) {
      resolved.put(name, resolve(name, raw));
    }
    return resolved;
  }

  private static Map<String, String> resolve(String name, Map<String, Element> raw) {
    Element style = raw.get(name);
    Map<String, String> items = new LinkedHashMap<>();
    if (style == null) {
      return items;
    }
    String parent =
        style.hasAttribute("parent") ? style.getAttribute("parent") : implicitParent(name);
    if (parent != null && !parent.isEmpty() && raw.containsKey(parent)) {
      items.putAll(resolve(parent, raw));
    }
    NodeList children = style.getElementsByTagName("item");
    for (int i = 0; i < children.getLength(); i++) {
      Element item = (Element) children.item(i);
      items.put(item.getAttribute("name"), item.getTextContent().trim());
    }
    return items;
  }

  private static String implicitParent(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : null;
  }

  /** Значение dimen в dp/sp, со следованием по ссылкам {@code @dimen/...}. */
  static float dimen(String value, Map<String, String> dimens) {
    String v = value;
    for (int guard = 0; v.startsWith("@dimen/") && guard < 8; guard++) {
      String ref = dimens.get(v.substring("@dimen/".length()));
      if (ref == null) {
        throw new AssertionError("Нет dimen " + v);
      }
      v = ref;
    }
    return Float.parseFloat(v.replaceAll("(dp|sp)$", ""));
  }
}
