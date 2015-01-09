/*
 *    MediathekView
 *    Copyright (C) 2008 - 2012     W. Xaver
 *                              &   thausherr
 * 
 *    W.Xaver[at]googlemail.com
 *    http://zdfmediathk.sourceforge.net/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package msearch.filmeSuchen.sender;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import msearch.daten.DatenFilm;
import msearch.filmeSuchen.MSFilmeSuchen;
import msearch.filmeSuchen.MSGetUrl;
import msearch.tool.MSConfig;
import msearch.tool.MSConst;
import msearch.tool.MSLog;
import msearch.tool.MSStringBuilder;

public class MediathekWdr extends MediathekReader implements Runnable {

    public final static String SENDERNAME = "WDR";
    private final static String ROCKPALAST_URL = "http://www1.wdr.de/fernsehen/kultur/rockpalast/videos/rockpalastvideos_konzerte100.html";
    private final static String ROCKPALAST_FESTIVAL = "http://www1.wdr.de/fernsehen/kultur/rockpalast/videos/rockpalastvideos_festivals100.html";
    private final ArrayList<String> listeFestival = new ArrayList<>();
    private final ArrayList<String> listeRochpalast = new ArrayList<>();
    private MSStringBuilder seite_1 = new MSStringBuilder(MSConst.STRING_BUFFER_START_BUFFER);
    private MSStringBuilder seite_2 = new MSStringBuilder(MSConst.STRING_BUFFER_START_BUFFER);

    public MediathekWdr(MSFilmeSuchen ssearch, int startPrio) {
        super(ssearch, SENDERNAME,/* threads */ 3, /* urlWarten */ 500, startPrio);
    }

    //===================================
    // public
    //===================================
    @Override
    public synchronized void addToList() {
        //Theman suchen
        listeThemen.clear();
        listeFestival.clear();
        listeRochpalast.clear();
        meldungStart();
        addToList__("http://www1.wdr.de/mediathek/video/sendungen/index.html");
        if (MSConfig.senderAllesLaden) {
            rockpalast();
            festival();
            // damit sie auch gestartet werden (im idealfall in unterschiedlichen Threads
            String[] add = new String[]{ROCKPALAST_URL, "Rockpalast"};
            listeThemen.addUrl(add);
            add = new String[]{ROCKPALAST_FESTIVAL, "Rockpalast"};
            listeThemen.addUrl(add);
        }
        // Sendung verpasst, da sind einige die nicht in einer "Sendung" enthalten sind
        // URLs nach dem Muster bauen:
        // http://www1.wdr.de/mediathek/video/sendungverpasst/sendung-verpasst-alles100_tag-03062013.html
        SimpleDateFormat formatter = new SimpleDateFormat("ddMMyyyy");
        String tag;
        for (int i = 1; i < 14; ++i) {
            final String URL = "http://www1.wdr.de/mediathek/video/sendungverpasst/sendung-verpasst-alles100_tag-";
            tag = formatter.format(new Date().getTime() - (1000 * 60 * 60 * 24 * i));
            String urlString = URL + tag + ".html";
            listeThemen.addUrl(new String[]{urlString, ""});
        }
        if (MSConfig.getStop()) {
            meldungThreadUndFertig();
        } else if (listeThemen.isEmpty() && listeFestival.isEmpty() && listeRochpalast.isEmpty()) {
            meldungThreadUndFertig();
        } else {
            meldungAddMax(listeThemen.size() + listeFestival.size() + listeRochpalast.size());
            for (int t = 0; t < maxThreadLaufen; ++t) {
                //new Thread(new ThemaLaden()).start();
                Thread th = new Thread(new ThemaLaden());
                th.setName(SENDERNAME + t);
                th.start();
            }
        }
    }

    //===================================
    // private
    //===================================
    private void rockpalast() {
        final String ROOTADR = "http://www.wdr.de/fernsehen/kultur/rockpalast/videos/";
        final String ITEM_1 = "<a href=\"/fernsehen/kultur/rockpalast/videos/";
        seite_1 = getUrlIo.getUri(SENDERNAME, ROCKPALAST_URL, MSConst.KODIERUNG_UTF, 3 /* versuche */, seite_1, "");
        try {
            seite_1.extractList(ITEM_1, "\"", 0, ROOTADR, listeRochpalast);
        } catch (Exception ex) {
            MSLog.fehlerMeldung(-915423698, MSLog.FEHLER_ART_MREADER, "MediathekWdr.rockpalast", ex);
        }
    }

    private void festival() {
        // http://www1.wdr.de/fernsehen/kultur/rockpalast/videos/rockpalastvideos_festivals100.html
        final String ROOTADR = "http://www1.wdr.de/fernsehen/kultur/rockpalast/videos/";
        final String ITEM_1 = "<a href=\"/fernsehen/kultur/rockpalast/videos/";
        seite_1 = getUrlIo.getUri(SENDERNAME, ROCKPALAST_FESTIVAL, MSConst.KODIERUNG_UTF, 3 /* versuche */, seite_1, "");
        try {
            seite_1.extractList(ITEM_1, "\"", 0, ROOTADR, listeFestival);
        } catch (Exception ex) {
            MSLog.fehlerMeldung(-432365698, MSLog.FEHLER_ART_MREADER, "MediathekWdr.festival", ex);
        }
    }

    private void addToList__(String ADRESSE) {
        // http://www1.wdr.de/mediathek/video/sendungen/abisz-b100.html
        //Theman suchen
        final String MUSTER_URL = "<a href=\"/mediathek/video/sendungen/abisz-";
        seite_1 = getUrlIo.getUri_Iso(SENDERNAME, ADRESSE, seite_1, "");
        int pos1 = 0;
        int pos2;
        String url;
        themenSeitenSuchen(ADRESSE); // ist die erste Seite: "a"
        while (!MSConfig.getStop() && (pos1 = seite_1.indexOf(MUSTER_URL, pos1)) != -1) {
            pos1 += MUSTER_URL.length();
            if ((pos2 = seite_1.indexOf("\"", pos1)) != -1) {
                url = seite_1.substring(pos1, pos2);
                if (url.equals("")) {
                    MSLog.fehlerMeldung(-995122047, MSLog.FEHLER_ART_MREADER, "MediathekWdr.addToList__", "keine URL");
                } else {
                    url = "http://www1.wdr.de/mediathek/video/sendungen/abisz-" + url;
                    themenSeitenSuchen(url);
                }
            }
        }
    }

    private void themenSeitenSuchen(String strUrlFeed) {
        final String MUSTER_START = "<ul class=\"linkList pictured\">";
        final String MUSTER_URL = "<a href=\"/mediathek/video/sendungen/";
        int pos1;
        int pos2;
        String url;
        seite_2 = getUrlIo.getUri_Iso(SENDERNAME, strUrlFeed, seite_2, "");
        meldung(strUrlFeed);
        if ((pos1 = seite_2.indexOf(MUSTER_START)) == -1) {
            MSLog.fehlerMeldung(-460857479, MSLog.FEHLER_ART_MREADER, "MediathekWdr.themenSeiteSuchen", "keine Url" + strUrlFeed);
            return;
        }
        while (!MSConfig.getStop() && (pos1 = seite_2.indexOf(MUSTER_URL, pos1)) != -1) {
            pos1 += MUSTER_URL.length();
            if ((pos2 = seite_2.indexOf("\"", pos1)) != -1) {
                url = seite_2.substring(pos1, pos2).trim();
                if (!url.equals("")) {
                    url = "http://www1.wdr.de/mediathek/video/sendungen/" + url;
                    //weiter gehts
                    String[] add;
                    add = new String[]{url, ""};
                    listeThemen.addUrl(add);
                }
            } else {
                MSLog.fehlerMeldung(-375862100, MSLog.FEHLER_ART_MREADER, "MediathekWdr.themenSeiteSuchen", "keine Url" + strUrlFeed);
            }
        }
    }

    private class ThemaLaden implements Runnable {

        MSGetUrl getUrl = new MSGetUrl(wartenSeiteLaden);
        private MSStringBuilder sendungsSeite1 = new MSStringBuilder(MSConst.STRING_BUFFER_START_BUFFER);
        private MSStringBuilder sendungsSeite2 = new MSStringBuilder(MSConst.STRING_BUFFER_START_BUFFER);
        private MSStringBuilder sendungsSeite3 = new MSStringBuilder(MSConst.STRING_BUFFER_START_BUFFER);
        private MSStringBuilder sendungsSeite4 = new MSStringBuilder(MSConst.STRING_BUFFER_START_BUFFER);
        private MSStringBuilder seiteRock1 = new MSStringBuilder(MSConst.STRING_BUFFER_START_BUFFER);

        @Override
        public void run() {
            try {
                meldungAddThread();
                String[] link;
                while (!MSConfig.getStop() && (link = listeThemen.getListeThemen()) != null) {
                    if (ROCKPALAST_URL.equals(link[0])) {
                        themenSeiteRockpalast();
                    } else if (ROCKPALAST_FESTIVAL.equals(link[0])) {
                        themenSeiteFestival();
                    } else {
                        sendungsSeitenSuchen1(link[0] /* url */);
                    }
                    meldungProgress(link[0]);
                }
            } catch (Exception ex) {
                MSLog.fehlerMeldung(-633250489, MSLog.FEHLER_ART_MREADER, "MediathekWdr.SenderThemaLaden.run", ex);
            }
            meldungThreadUndFertig();
        }

        private void sendungsSeitenSuchen1(String strUrl) {
            // http://www1.wdr.de/mediathek/video/sendungen/ein_fall_fuer_die_anrheiner/filterseite-ein-fall-fuer-die-anrheiner100_compage-2_paginationId-picturedList0.html#picturedList0
            int pos1;
            int pos2;
            int ende;
            meldung(strUrl);
            // Sendungen auf der Seite
            sendungsSeitenSuchen2(strUrl);
            if (!MSConfig.senderAllesLaden) {
                // dann wars das
                return;
            }
            sendungsSeite1 = getUrl.getUri_Utf(SENDERNAME, strUrl, sendungsSeite1, "");
            // weitere Seiten suchen
            if ((pos1 = sendungsSeite1.indexOf("<ul class=\"pageCounterNavi\">")) == -1) {
                return;
            }
            if ((ende = sendungsSeite1.indexOf("</ul>", pos1)) == -1) {
                return;
            }
            while ((pos1 = sendungsSeite1.indexOf("<a href=\"/mediathek/video/sendungen/", pos1)) != -1) {
                if (pos1 > ende) {
                    // dann wars das
                    return;
                }
                pos1 += "<a href=\"/mediathek/video/sendungen/".length();
                if ((pos2 = sendungsSeite1.indexOf("\"", pos1)) != -1) {
                    String urlWeiter = sendungsSeite1.substring(pos1, pos2);
                    if (!urlWeiter.equals("")) {
                        // Sendungen auf der Seite
                        sendungsSeitenSuchen2("http://www1.wdr.de/mediathek/video/sendungen/" + urlWeiter);
                    }
                }
            }
        }

        private void sendungsSeitenSuchen2(String strUrl) {
            final String MUSTER_START_1 = "<ul class=\"linkList pictured\">";
            final String MUSTER_START_2 = "<div id=\"pageLeadIn\">";

            final String MUSTER_URL = "<a href=\"/mediathek/video/sendungen/";
            final String MUSTER_TITEL = "<strong>";
            final String MUSTER_DAUER = "<span class=\"hidden\">L&auml;nge: </span>";
            final String MUSTER_THEMA = "<title>";
            int pos;
            int pos1;
            int pos2;
            int ende;
            String url;
            String titel;
            String dauer;
            String datum = "";
            String thema;
            long duration = 0;
            boolean verpasst = false;
            pos = 0;
            if (strUrl.startsWith("http://www1.wdr.de/mediathek/video/sendungen/lokalzeit/uebersicht-lokalzeiten100_tag")) {
                // brauchts nicht
                return;
            }
            sendungsSeite2 = getUrl.getUri_Utf(SENDERNAME, strUrl, sendungsSeite2, "");
            meldung(strUrl);

            thema = sendungsSeite2.extract(MUSTER_THEMA, "<", pos);
            thema = thema.replace("- WDR MEDIATHEK", "").trim();
            if (thema.startsWith("Sendung verpasst ")) {
                verpasst = true;
            }
            // und jetzt die Beiträge
            if ((pos = sendungsSeite2.indexOf(MUSTER_START_1)) == -1) {
                if ((pos = sendungsSeite2.indexOf(MUSTER_START_2)) == -1) {
                    MSLog.fehlerMeldung(-765323079, MSLog.FEHLER_ART_MREADER, "MediathekWdr.sendungsSeiteSuchen", "keine Url" + strUrl);
                    return;
                }
            }
            if ((ende = sendungsSeite2.indexOf("<ul class=\"pageCounterNavi\">", pos)) == -1) {
                if ((ende = sendungsSeite2.indexOf("<div id=\"socialBookmarks\">", pos)) == -1) {
                    if ((ende = sendungsSeite2.indexOf("<span>Hilfe zur Steuerung der \"Sendung verpasst\"")) == -1) {
                        MSLog.fehlerMeldung(-646897321, MSLog.FEHLER_ART_MREADER, "MediathekWdr.sendungsSeiteSuchen", "keine Url" + strUrl);
                        return;
                    }
                }
            }
            while (!MSConfig.getStop() && (pos = sendungsSeite2.indexOf(MUSTER_URL, pos)) != -1) {
                if (pos > ende) {
                    break;
                }
                pos += MUSTER_URL.length();
                pos1 = pos;
                if ((pos2 = sendungsSeite2.indexOf("\"", pos)) != -1) {
                    url = sendungsSeite2.substring(pos1, pos2).trim();
                    if (!url.equals("")) {
                        url = "http://www1.wdr.de/mediathek/video/sendungen/" + url;

                        titel = sendungsSeite2.extract(MUSTER_TITEL, "<", pos).trim();
                        if (verpasst) {
                            thema = "";
                            // dann Thema aus dem Titel
                            if (titel.contains(":")) {
                                thema = titel.substring(0, titel.indexOf(":")).trim();
                                if (thema.contains(" - ")) {
                                    thema = thema.substring(0, thema.indexOf(" - ")).trim();
                                }
                            }
                        }
                        // putzen
                        titel = titel.replace("\n", "");
                        if (titel.contains("-")) {
                            titel = titel.substring(titel.indexOf("-") + 1, titel.length());
                        }
                        if (titel.contains(":")) {
                            datum = titel.substring(titel.lastIndexOf(":") + 1, titel.length()).trim();
                            if (datum.contains(" vom")) {
                                datum = datum.substring(datum.indexOf(" vom") + " vom".length()).trim();
                            }
                            titel = titel.substring(0, titel.lastIndexOf(":")).trim();
                        }

                        dauer = sendungsSeite2.extract(MUSTER_DAUER, "<", pos).trim();
                        try {
                            if (!dauer.equals("")) {
                                String[] parts = dauer.split(":");
                                duration = 0;
                                long power = 1;
                                for (int i = parts.length - 1; i >= 0; i--) {
                                    duration += Long.parseLong(parts[i]) * power;
                                    power *= 60;
                                }
                            }
                        } catch (Exception ex) {
                            MSLog.fehlerMeldung(-306597519, MSLog.FEHLER_ART_MREADER, "MediathekWdr.sendungsSeiteSuchen", ex, strUrl);
                        }

                        //weiter gehts
                        addFilm1(thema, titel, url, duration, datum);
                    } else {
                        MSLog.fehlerMeldung(-646432970, MSLog.FEHLER_ART_MREADER, "MediathekWdr.sendungsSeiteSuchen-1", "keine Url" + strUrl);
                    }
                }
            }
        }

        private void addFilm1(String thema, String titel, String filmWebsite, long dauer, String datum) {
            // http://www1.wdr.de/mediathek/video/sendungen/die_story/videopharmasklaven100-videoplayer_size-L.html

            final String MUSTER_URL_START = "<span class=\"videoLink\"";
            final String MUSTER_URL = "<a href=\"/mediathek/video/sendungen/";
            final String MUSTER_DESCRIPTION = "<meta name=\"Description\" content=\"";

            final String MUSTER_KEYWORDS = "<meta name=\"Keywords\" content=\"";
            meldung(filmWebsite);
            sendungsSeite3 = getUrl.getUri_Utf(SENDERNAME, filmWebsite, sendungsSeite3, "");
            String url;
            String description;
            String[] keywords;
            description = sendungsSeite3.extract(MUSTER_DESCRIPTION, "\"");
            String k = sendungsSeite3.extract(MUSTER_KEYWORDS, "\"");
            keywords = k.split(", ");

            // URL suchen
            url = sendungsSeite3.extract(MUSTER_URL_START, MUSTER_URL, "\"");
            if (url.isEmpty()) {
                url = sendungsSeite3.extract("<li class=\"mediathekvideo\" >", MUSTER_URL, "\"");
            }

            if (!url.equals("")) {
                addFilm2(filmWebsite, thema, titel, "http://www1.wdr.de/mediathek/video/sendungen/" + url, dauer, datum, description, keywords);
            } else {
                MSLog.fehlerMeldung(-763299001, MSLog.FEHLER_ART_MREADER, "MediathekWdr.addFilme1", new String[]{"keine Url: " + filmWebsite});
            }

        }

        private void addFilm2(String filmWebsite, String thema, String titel, String urlFilmSuchen, long dauer, String datum, String beschreibung, String[] keyword) {
            // ;dslSrc=rtmp://gffstream.fcod.llnwd.net/a792/e1/media/video/2009/02/14/20090214_a40_komplett_big.flv&amp;isdnSrc=rtm
            // <p class="wsArticleAutor">Ein Beitrag von Heinke Schröder, 24.11.2010	</p>
            final String MUSTER_URL_L = "<a rel=\"webL\"  href=\"";
            final String MUSTER_URL_M = "<a rel=\"webM\"  href=\"";
            final String MUSTER_URL_S = "<a rel=\"webS\"  href=\"";
            meldung(urlFilmSuchen);
            sendungsSeite4 = getUrl.getUri_Utf(SENDERNAME, urlFilmSuchen, sendungsSeite4, "");
            String url;
            String urlKlein;

            // URL suchen
            url = sendungsSeite4.extract(MUSTER_URL_L, "\"");
            urlKlein = sendungsSeite4.extract(MUSTER_URL_M, "\"");
            if (url.equals("")) {
                url = urlKlein;
                urlKlein = "";
            }
            if (url.equals("")) {
                url = sendungsSeite4.extract(MUSTER_URL_S, "\"");
            } else if (urlKlein.equals("")) {
                urlKlein = sendungsSeite4.extract(MUSTER_URL_S, "\"");
            }

            if (url.isEmpty()) {
                url = sendungsSeite4.extract("<a rel=\"adaptiv\" type=\"application/vnd.apple.mpegURL\" href=\"", "\"");
            }
            if (datum.isEmpty()) {
                String d = sendungsSeite4.extract("<meta name=\"DC.Date\" content=\"", "\"");
                datum = convertDatum(d);
            }
            if (!url.isEmpty()) {
                // URL bauen von
                final String mobileUrl = "http://mobile-ondemand.wdr.de/";
                final String replaceUrl = "http://http-ras.wdr.de/";
                url = url.replace(mobileUrl, replaceUrl);
                urlKlein = urlKlein.replace(mobileUrl, replaceUrl);

                DatenFilm film = new DatenFilm(SENDERNAME, thema, filmWebsite, titel, url, ""/*rtmpURL*/, datum, ""/* zeit */,
                        dauer, beschreibung, keyword);
                film.addUrlKlein(urlKlein, "");
                addFilm(film);
            } else {
                MSLog.fehlerMeldung(-978451239, MSLog.FEHLER_ART_MREADER, "MediathekWdr.addFilme2", new String[]{"keine Url: " + urlFilmSuchen, "UrlThema: " + filmWebsite});
            }
        }

        private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmXXX");// 2014-07-07T00:35+01:00
        private final SimpleDateFormat sdfOutDay = new SimpleDateFormat("dd.MM.yyyy");

        private String convertDatum(String datum) {
            try {
                Date filmDate = sdf.parse(datum);
                datum = sdfOutDay.format(filmDate);
            } catch (ParseException ex) {
                MSLog.fehlerMeldung(-731025789, MSLog.FEHLER_ART_PROG, "MediathekKika.convertDatum: " + datum, ex);
            }
            return datum;
        }

        private void themenSeiteRockpalast() {
            try {
                for (String s : listeRochpalast) {
                    meldungProgress(s);
                    if (MSConfig.getStop()) {
                        break;
                    }
                    if (s.endsWith("/fernsehen/kultur/rockpalast/videos/index.html")) {
                        continue;
                    }
                    if (s.contains("/fernsehen/kultur/rockpalast/videos/uebersicht_Konzerte10")) {
                        continue;
                    }
                    // Konzerte suchen
                    addFilmeRockpalast(s, "Rockpalast");
                }
            } catch (Exception ex) {
                MSLog.fehlerMeldung(-696963025, MSLog.FEHLER_ART_MREADER, "MediathekWdr.themenSeiteRockpalast", ex);
            }
        }

        private void themenSeiteFestival() {
            try {
                for (String s : listeFestival) {
                    meldungProgress(s);
                    if (MSConfig.getStop()) {
                        break;
                    }
                    if (s.endsWith("/fernsehen/kultur/rockpalast/videos/index.html")) {
                        continue;
                    }
                    if (s.contains("/fernsehen/kultur/rockpalast/videos/uebersicht_Festivals116.html")) {
                        continue;
                    }
                    if (s.endsWith("/uebersicht_Festival100.html")) {
                        continue;
                    }
                    if (s.endsWith("/uebersicht_Festivals100.html")) {
                        continue;
                    }
                    // Konzerte suchen
                    addFilmeRockpalast(s, "Rockpalast - Festival");
                }
            } catch (Exception ex) {
                MSLog.fehlerMeldung(-915263698, MSLog.FEHLER_ART_MREADER, "MediathekWdr.themenSeiteRockpalast", ex);
            }
        }

        private void addFilmeRockpalast(String filmWebsite, String thema) {
            meldung(filmWebsite);
            seiteRock1 = getUrl.getUri_Utf(SENDERNAME, filmWebsite, seiteRock1, "");
            String url;
            long duration = 0;
            String description;
            String[] keywords;

            String titel = seiteRock1.extract("headline: \"", "\"");
            titel = titel.replace("\n", "");

            String d = seiteRock1.extract("length: \"(", ")");
            try {
                if (!d.equals("") && d.length() <= 8 && d.contains(":")) {
                    String[] parts = d.split(":");
                    long power = 1;
                    for (int i = parts.length - 1; i >= 0; i--) {
                        duration += Long.parseLong(parts[i]) * power;
                        power *= 60;
                    }
                }
            } catch (Exception ex) {
                MSLog.fehlerMeldung(-302058974, MSLog.FEHLER_ART_MREADER, "MediathekWdr.addFilme2-1", ex, "duration: " + d);
            }

            description = seiteRock1.extract("<meta name=\"Description\" content=\"", "\"");

            String k = seiteRock1.extract("<meta name=\"Keywords\" content=\"", "\"");
            keywords = k.split(", ");

            String datum = seiteRock1.extract("Konzert vom", "\"").trim();
            if (datum.isEmpty()) {
                datum = seiteRock1.extract("Sendung vom ", "\"").trim();
            }
            url = seiteRock1.extract("<a href=\"/fernsehen/kultur/rockpalast/videos/av/", "\"");
            if (url.isEmpty()) {
                MSLog.fehlerMeldung(-915236547, MSLog.FEHLER_ART_MREADER, "MediathekWdr.addFilmeRockpalast", "keine URL: " + filmWebsite);
            } else {
                url = "http://www1.wdr.de/fernsehen/kultur/rockpalast/videos/av/" + url;
                addFilm2(filmWebsite, thema, titel, url, duration, datum, description, keywords);
            }
        }
    }
}
