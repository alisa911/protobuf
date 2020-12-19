package protobuf;

import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.input.CountingInputStream;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
    private static final HashSet<String> COUNTRY_NAMES = new HashSet<>();

    @Override
    public void generateCountryFile() {
        try {
            String urlRegionsFile = getUrlAfterRedirect();
            Document documentFromRegionsXml = loadDocumentFromUrl(urlRegionsFile);

            List<RegionProtos.Region.Builder> regionsProtos = parsingXml(documentFromRegionsXml);
            List<RegionProtos.Region> regionProtosList = parsingPolygons(regionsProtos);

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
            FileInputStream stream = new FileInputStream(result);
            CountingInputStream countingStream = new CountingInputStream(stream);
            do {
                region = RegionProtos.Region.parseDelimitedFrom(countingStream);
                long bytesRead = countingStream.getCount();
                if (region.getName().equals(countryName)) {
                    logStat(result, bytesRead);
                    break;
                }
            } while (true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return region;
    }

    @Override
    public ArrayList<String> findCountryByPoint(File result, double lat, double lon) {
        ArrayList<String> countryNames = new ArrayList<>();
        RegionProtos.Region region;
        try {
            FileInputStream stream = new FileInputStream(result);
            CountingInputStream countingStream = new CountingInputStream(stream);
            region = RegionProtos.Region.parseDelimitedFrom(countingStream);
            long bytesRead;
            do {
                if (region.getPointList() != null) {
                    List<RegionProtos.Region.Point> polygon = region.getPointList();
                    if (isPointInPolygon(lat, lon, polygon)) {
                        countryNames.add(region.getName());
                    }
                }
                region = RegionProtos.Region.parseDelimitedFrom(stream);
                bytesRead = countingStream.getCount();
            } while (region != null);
            logStat(result, bytesRead);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("read country from file= "+countryNames.toString());
        return countryNames;
    }

    private void logStat(File result, long bytesRead) {
        long sizeFile = result.length();
        System.out.println("sizeFile sizeFile= "+sizeFile);
        double per = (double) bytesRead * 100 / sizeFile;
        System.out.println("read bytes from file= "+bytesRead);
        System.out.println("percent= "+String.format("%.2f", per));
    }

    private boolean isPointInPolygon(double lat, double lon, List<RegionProtos.Region.Point> polygon) {
        boolean res = false;
        int j = polygon.size() - 1;

        for (int i = 0; i < polygon.size(); i++) {
            if ((((polygon.get(i).getLat() < lat) && (lat < polygon.get(j).getLat()))
                    || ((polygon.get(j).getLat() < lat) && (lat < polygon.get(i).getLat()))) &&
                    (lon > (polygon.get(j).getLon() - polygon.get(i).getLon()) * (lat - polygon.get(i).getLat())
                            / (polygon.get(j).getLat() - polygon.get(i).getLat()) + polygon.get(i).getLon()))
                res = !res;
            j = i;
        }

        return res;
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
            Element e = (Element) regions.item(i);
            if (regions.item(i).getParentNode().getNodeName().equals("region") && e.hasAttribute("poly_extract")) {
                regionProtosList.add(createRegionByXML(e));
            }
        }
        return regionProtosList;
    }

    private RegionProtos.Region.Builder createRegionByXML(Element e) {
        RegionProtos.Region.Builder region = RegionProtos.Region.newBuilder();
        String name = e.getAttribute("name");
        COUNTRY_NAMES.add(name);
        region.setName(name);
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

    private List<RegionProtos.Region> parsingPolygons(List<RegionProtos.Region.Builder> regionsProtos) throws Exception {
        List<File> polygons = saveZip();
        List<File> actualPolygons = getActualPolygons(polygons);
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

    private List<File> getActualPolygons(List<File> polygons) {

        List<File> actualPolygons = new ArrayList<>();
        for (File file : polygons) {
            for (String name : COUNTRY_NAMES) {
                if (file.getName().endsWith(name + ".poly")) {
                    actualPolygons.add(file);
                }
            }
        }
        return actualPolygons;
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
