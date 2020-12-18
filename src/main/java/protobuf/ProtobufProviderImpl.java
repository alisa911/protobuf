package protobuf;

import net.lingala.zip4j.ZipFile;
import org.jsoup.Jsoup;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class ProtobufProviderImpl implements ProtobufProvider<RegionProtos.Region> {

    public static final File RESULT_FILE = new File("./src/main/resources/result");
    private static final String URL_REGIONS = "https://drive.google.com/u/0/uc?id=1LtPDgdUjAv9xEESDqZcrM5VJ1-EpPVaQ&export-download";

    @Override
    public void generateCountryFile() {
        try {
            String urlRegionsFile = getUrlAfterRedirect();
            Document documentFromRegionsXml = loadDocumentFromUrl(urlRegionsFile);

            List<RegionProtos.Region.Builder> regionsProtos = parsingXml(documentFromRegionsXml);
            List<RegionProtos.Region> regionProtosList = parsingPolygons(regionsProtos, documentFromRegionsXml);

            FileOutputStream stream = new FileOutputStream(RESULT_FILE);
            for (RegionProtos.Region region : regionProtosList) {
                region.writeDelimitedTo(stream);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public RegionProtos.Region findCountryByName(File result, String countryName) {
        RegionProtos.Region region = null;
        try {
            do {
                region = RegionProtos.Region.parseDelimitedFrom(new FileInputStream(result));
                if (region.getName().equals(countryName)) {
                    System.out.println("read from file: \n" + region.getName());
                    break;
                }
            } while (true);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return region;
    }

    private String getUrlAfterRedirect() {
        try {
            return Jsoup.connect(URL_REGIONS)
                    .followRedirects(true)
                    .execute()
                    .url()
                    .toExternalForm();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Document loadDocumentFromUrl(String url) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new URL(url).openStream());
    }

    private List<RegionProtos.Region.Builder> parsingXml(Document document) {
        NodeList regions = document.getElementsByTagName("region");
        List<RegionProtos.Region.Builder> regionProtosList = new ArrayList<>();

        for (int i = 0; i < regions.getLength(); i++) {
            if (regions.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Node el = regions.item(i);
                for (int j = 0; j < el.getChildNodes().getLength(); j++) {
                    Node n = el.getChildNodes().item(j);
                    if (n instanceof Element) {
                        Element e = (Element) n;

                        regionProtosList.add(createRegionByXML(e));

                    }
                }
            }
        }
        return regionProtosList;
    }

    private RegionProtos.Region.Builder createRegionByXML(Element e) {
        RegionProtos.Region.Builder region = RegionProtos.Region.newBuilder();
        region.setName(e.getAttribute("name"));
        if (setField(e, "lang")) region.setLang(e.getAttribute("lang"));
        if (setField(e, "type")) region.setType(e.getAttribute("type"));
        if (setField(e, "roads")) region.setLang(e.getAttribute("roads"));
        if (setField(e, "translate")) region.setLang(e.getAttribute("translate"));
        if (setField(e, "srtm")) region.setLang(e.getAttribute("srtm"));
        if (setField(e, "hillshade")) region.setLang(e.getAttribute("hillshade"));
        if (setField(e, "wiki")) region.setLang(e.getAttribute("wiki"));

        return region;
    }

    private static boolean setField(Element e, String str) {
        String value = e.getAttribute(str);
        return value.length() > 0;
    }

    private List<RegionProtos.Region> parsingPolygons(List<RegionProtos.Region.Builder> regionsProtos,
                                                      Document documentFromRegionsXml) throws Exception {
        List<File> polygons = saveZip();
        List<File> actualPolygons = getActualPolygons(documentFromRegionsXml, polygons);
        List<RegionProtos.Region> regionsResult = new ArrayList<>();
        for (RegionProtos.Region.Builder builder : regionsProtos) {
            for (File file : actualPolygons) {
                if (file.getName().endsWith(builder.getName() + ".poly")) {
                    getPoints(file, builder);
                    regionsResult.add(builder.build());
                }
            }
        }
        return regionsResult;
    }

    private List<File> saveZip() throws Exception {
        Path tempDir = Files.createTempDirectory("protobuf");
        String zipPath = tempDir + "polygons.zip";
        String unzipPath = tempDir + "/polygons";

        String urlPolygons = "https://github.com/osmandapp/OsmAnd-misc/archive/master.zip";

        try {
            new FileOutputStream(zipPath).getChannel()
                    .transferFrom(Channels.newChannel(new URL(urlPolygons).openStream()), 0, Long.MAX_VALUE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        unzip(zipPath, unzipPath);

        return Files.walk(Paths.get(unzipPath))
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .collect(Collectors.toList());
    }

    private void unzip(String zipPath, String unzipPath) throws Exception {
        ZipFile zipFile = new ZipFile(zipPath);
        zipFile.extractAll(unzipPath);
    }

    private List<File> getActualPolygons(Document documentFromRegionsXml, List<File> polygons) {
        HashSet<String> countriesByXml = getRegionsByXml(documentFromRegionsXml);

        List<File> actualPolygons = new ArrayList<>();
        for (File file : polygons) {
            for (String name : countriesByXml) {
                if (file.getName().endsWith(name + ".poly")) {
                    actualPolygons.add(file);
                }
            }
        }
        return actualPolygons;
    }

    private HashSet<String> getRegionsByXml(Document document) {
        HashSet<String> names = new HashSet<>();
        NodeList regions = document.getElementsByTagName("region");

        for (int i = 0; i < regions.getLength(); i++) {
            if (regions.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Node el = regions.item(i);
                for (int j = 0; j < el.getChildNodes().getLength(); j++) {
                    Node n = el.getChildNodes().item(j);
                    if (n instanceof Element) {
                        Element e = (Element) n;
                        names.add(e.getAttribute("name"));
                    }
                }
            }
        }
        return names;
    }

    private void getPoints(File file, RegionProtos.Region.Builder builder) {
        RegionProtos.Region.Point.Builder point = RegionProtos.Region.Point.newBuilder();
        try {
            FileReader fr = new FileReader(file);
            BufferedReader reader = new BufferedReader(fr);
            reader.readLine();
            reader.readLine();
            String line = reader.readLine();
            while (!line.equals("END")) {
                String[] str = line.trim().replaceAll("\\s+", " ").split(" ");
                point.setLat(Double.parseDouble(str[0]));
                point.setLon(Double.parseDouble(str[1]));
                point.build();
                builder.addPoint(point);
                line = reader.readLine();
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
    }
}
