package net.mabako.steamgifts.tasks;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import net.mabako.Constants;
import net.mabako.steamgifts.data.Comment;
import net.mabako.steamgifts.data.Game;
import net.mabako.steamgifts.data.GameInfo;
import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.data.ICommentHolder;
import net.mabako.steamgifts.data.IImageHolder;
import net.mabako.steamgifts.data.Image;
import net.mabako.steamgifts.persistentdata.SavedGameInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Utils {
    private Utils() {
    }

    /**
     * Extract comments recursively.
     *
     * @param commentNode Jsoup-Node of the parent node.
     * @param parent
     */
    public static void loadComments(Element commentNode, ICommentHolder parent) {
        loadComments(commentNode, parent, 0, false);
    }

    public static void loadComments(Element commentNode, ICommentHolder parent, int depth, boolean reversed) {
        if (commentNode == null)
            return;

        Elements children = commentNode.children();

        if (reversed)
            Collections.reverse(children);

        for (Element c : children) {
            long commentId = 0;
            try {
                commentId = Integer.parseInt(c.attr("data-comment-id"));
            } catch (NumberFormatException e) {
                /* do nothing */
            }

            Comment comment = loadComment(c.child(0), commentId, depth);

            // add this
            parent.addComment(comment);

            // Add all children
            loadComments(c.select(".comment__children").first(), parent, depth + 1, false);
        }
    }

    /**
     * Load a single comment
     *
     * @param element   comment HTML element
     * @param commentId the id of  the comment to be loaded
     * @param depth     the depth at which to display said comment
     * @return the new comment
     */
    @NonNull
    public static Comment loadComment(Element element, long commentId, int depth) {
        // Save the content of the edit state for a bit & remove the edit state from being rendered.
        Element editState = element.select(".comment__edit-state.is-hidden textarea[name=description]").first();
        String editableContent = null;
        if (editState != null)
            editableContent = editState.text();
        element.select(".comment__edit-state").html("");

        Element authorNode = element.select(".comment__username").first();
        String author = authorNode.text();
        boolean isOp = authorNode.hasClass("comment__username--op");

        String avatar = null;
        Element avatarNode = element.select(".global__image-inner-wrap").first();
        if (avatarNode != null)
            avatar = extractAvatar(avatarNode.attr("style"));

        Element timeCreated = element.select(".comment__actions > div span").first();

        Uri permalinkUri = Uri.parse(element.select(".comment__actions a[href^=/go/comment").first().attr("href"));

        Comment comment = new Comment(commentId, author, depth, avatar, isOp);
        comment.setPermalinkId(permalinkUri.getPathSegments().get(2));
        comment.setEditableContent(editableContent);
        comment.setCreatedTime(timeCreated.attr("title"));


        Element desc = element.select(".comment__description").first();
        String content = loadAttachedImages(comment, desc);
        comment.setContent(content);

        // check if the comment is deleted
        comment.setDeleted(element.select(".comment__summary").first().select(".comment__delete-state").size() == 1);

        comment.setHighlighted(element.select(".comment__parent > .comment__envelope").size() != 0);

        Element roleName = element.select(".comment__role-name").first();
        if (roleName != null)
            comment.setAuthorRole(roleName.text().replace("(", "").replace(")", ""));

        // Do we have either a delete or undelete link?
        comment.setDeletable(element.select(".comment__actions__button.js__comment-delete").size() + element.select(".comment__actions__button.js__comment-undelete").size() == 1);
        return comment;
    }

    public static String extractAvatar(String style) {
        return style.replace("background-image:url(", "").replace(");", "").replace("_medium", "_full");
    }

    /**
     * Load some details for the giveaway. Some items must be loaded outside of this.
     */
    public static void loadGiveaway(Giveaway giveaway, Element element, String cssNode, String headerHintCssNode, Uri steamUri) {
        // Copies & Points. They do not have separate markup classes, it's basically "if one thin markup element exists, it's one copy only"
        Elements hints = element.select("." + headerHintCssNode);
        if (!hints.isEmpty()) {
            String copiesT = hints.first().text();
            String pointsT = hints.last().text();
            int copies = hints.size() == 1 ? 1 : Integer.parseInt(copiesT.replace("(", "").replace(" Copies)", "").replace(",", ""));
            int points = Integer.parseInt(pointsT.replace("(", "").replace("P)", ""));

            giveaway.setCopies(copies);
            giveaway.setPoints(points);
        } else {
            giveaway.setCopies(1);
            giveaway.setPoints(0);
        }

        // Steam link
        if (steamUri != null) {
            List<String> pathSegments = steamUri.getPathSegments();
            if (pathSegments.size() >= 2) {
                giveaway.setGame(new Game("app".equals(pathSegments.get(0)) ? Game.Type.APP : Game.Type.SUB, Integer.parseInt(pathSegments.get(1))));
            }
        }

        // Time remaining

        Element end = element.select("." + cssNode + "__columns > div span").first();
        giveaway.setEndTime(end.attr("title"), end.text());
        giveaway.setCreatedTime(element.select("." + cssNode + "__columns > div span").last().attr("title"));

        // Flags
        giveaway.setWhitelist(!element.select("." + cssNode + "__column--whitelist").isEmpty());
        giveaway.setGroup(!element.select("." + cssNode + "__column--group").isEmpty());
        giveaway.setPrivate(!element.select("." + cssNode + "__column--invite-only").isEmpty());
        giveaway.setRegionRestricted(!element.select("." + cssNode + "__column--region-restricted").isEmpty());

        Element level = element.select("." + cssNode + "__column--contributor-level").first();
        if (level != null)
            giveaway.setLevel(Integer.parseInt(level.text().replace("Level", "").replace("+", "").trim()));

        // Internal ID for blacklisting
        Element popup = element.select(".giveaway__hide.trigger-popup").first();
        if (popup != null)
            giveaway.setInternalGameId(Integer.parseInt(popup.attr("data-game-id")));
    }

    /**
     * Loads giveaways from a list page.
     * <p>This is not suitable for loading individual giveaway instances from the featured list, as the HTML layout differs (see {@link LoadGiveawayDetailsTask#loadGiveaway(Document, Uri)}</p>
     *
     * @param document the loaded document
     * @return list of giveaways
     */
    public static List<Giveaway> loadGiveawaysFromList(Document document, SavedGameInfo savedGameInfo) {
        Elements giveaways = document.select(".giveaway__row-inner-wrap");

        List<Giveaway> giveawayList = new ArrayList<>();
        for (Element element : giveaways) {
            // Basic information
            Element link = element.select("h2 a").first();

            Giveaway giveaway = null;
            if (link.hasAttr("href")) {
                Uri linkUri = Uri.parse(link.attr("href"));
                String giveawayLink = linkUri.getPathSegments().get(1);
                String giveawayName = linkUri.getPathSegments().get(2);

                giveaway = new Giveaway(giveawayLink);
                giveaway.setName(giveawayName);
            } else {
                giveaway = new Giveaway(null);
                giveaway.setName(null);
            }

            giveaway.setTitle(link.text());
            giveaway.setCreator(element.select(".giveaway__username").text());

            // Entries, would usually have comment count too... but we don't display that anywhere.
            Elements links = element.select(".giveaway__links a span");
            giveaway.setEntries(Integer.parseInt(links.first().text().split(" ")[0].replace(",", "")));

            giveaway.setEntered(element.hasClass("is-faded"));

            // More details
            Element icon = element.select("h2 a").last();
            Uri uriIcon = icon == link ? null : Uri.parse(icon.attr("href"));

            Utils.loadGiveaway(giveaway, element, "giveaway", "giveaway__heading__thin", uriIcon);
            giveawayList.add(giveaway);

            applyGiveawayRating(giveaway, savedGameInfo);
        }

        return giveawayList;
    }

    private static boolean isWifi(Context context) {
        return Utils.isConnectedToWifi("Utils", context);
    }

    public static void applyGiveawayRating(Giveaway giveaway, SavedGameInfo savedGameInfo) {
        GameInfo gameInfo = savedGameInfo.get(giveaway.getGameId());

        if (!isWifi(savedGameInfo.getContext())) {
            if (gameInfo != null) {
                gameInfo.updateGiveaway(giveaway);
            }
            return;
        }

        if (gameInfo == null || !gameInfo.isValid()) {
            gameInfo = fetchGameInfo(giveaway.getGameId());
            if (gameInfo != null) {
                savedGameInfo.add(gameInfo, gameInfo.getGameId());
            }
        }

        if (gameInfo == null) {
            return;
        }

//        Set<String> newTags = new HashSet<>();
//        for (String tag : gameInfo.getTags()) {
//            newTags.add(tag.trim());
//        }
//        gameInfo.getTags().clear();
//        gameInfo.getTags().addAll(newTags);
//        savedGameInfo.add(gameInfo, gameInfo.getGameId());

        gameInfo.updateGiveaway(giveaway);
    }

    public static GameInfo fetchGameInfo(int gameId) {
        GameInfo gameInfo = new GameInfo(gameId, System.currentTimeMillis());
        updateGameInfoFromSteamDB(gameInfo);
        updateGameInfoFromMetacritics(gameInfo);
        updateGameInfoFromSteamPowered(gameInfo);
        return gameInfo;
    }

    private static void updateGameInfoFromSteamDB(GameInfo gameInfo) {
        try {
            Connection connect = Jsoup.connect("https://steamdb.info/app/" + gameInfo.getGameId())
                    .userAgent(Constants.JSOUP_USER_AGENT)
                    .timeout(Constants.JSOUP_TIMEOUT);

            Document document = connect.get();
            String s = document.toString();

            String ratingText = getValueFromHtml(s, "ratingValue");
            if (ratingText != null) {
                gameInfo.updateRating(Integer.parseInt(ratingText));
            }

            gameInfo.getTags().addAll(searchTags(s, "/tags/?tagid", 0));


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Set<String> searchTags(String s, String subString, int startIdx) {
        Set<String> tags = new HashSet<>();
        int idx = s.indexOf(subString, startIdx);
        while (idx != -1) {
            int start = s.indexOf(">", idx) + 1;
            int end = s.indexOf("</a>", start);

            String tag = s.substring(start, end);
            tags.add(tag.trim());

            idx = s.indexOf(subString, end);
        }

        return tags;
    }

    private static void updateGameInfoFromSteamPowered(GameInfo gameInfo) {
        try {
            Connection connect = Jsoup.connect("http://store.steampowered.com/app/" + gameInfo.getGameId())
                    .userAgent(Constants.JSOUP_USER_AGENT)
                    .timeout(Constants.JSOUP_TIMEOUT);

            Document document = connect.get();
            String s = document.toString();

            String ratingText = getValueFromHtml(s, "ratingValue");

            if (ratingText != null) {
                gameInfo.updateRating(Integer.parseInt(ratingText) * 10);
            }

            int glance_tags = s.indexOf("glance_tags");

            Set<String> tags = searchTags(s, "http://store.steampowered.com/tag", glance_tags);
            gameInfo.getTags().addAll(tags);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void updateGameInfoFromMetacritics(GameInfo gameInfo) {
        String fileData = doGet("http://store.steampowered.com/api/appdetails?appids=" + gameInfo.getGameId());

        try {
            JSONObject jsonObject = new JSONObject(fileData);
            JSONObject gameJsonObject = jsonObject.getJSONObject(Integer.toString(gameInfo.getGameId()));
            JSONObject dataJsonObject = gameJsonObject.getJSONObject("data");
            JSONObject metacriticJsonObject = dataJsonObject.getJSONObject("metacritic");
            gameInfo.updateRating(metacriticJsonObject.getInt("score"));

            JSONArray genresJsonArray = dataJsonObject.getJSONArray("genres");
            for (int i = 0; i < genresJsonArray.length(); i++) {
                JSONObject jsonObject1 = genresJsonArray.getJSONObject(i);
                String description = jsonObject1.getString("description");
                gameInfo.getTags().add(description.trim());
            }
        } catch (JSONException e) {
        }
    }

    @NonNull
    private static String doGet(String urlPath) {
        StringBuilder total = new StringBuilder();
        try {
            URL url = new URL(urlPath);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            try {
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                BufferedReader r = new BufferedReader(new InputStreamReader(in));

                String line;
                while ((line = r.readLine()) != null) {
                    total.append(line);
                }
            } finally {
                urlConnection.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return total.toString();
    }


    private static String getValueFromHtml(String body, String valueName) {
        int startIdx = body.indexOf(valueName);
        if (startIdx != -1) {
            int contentStartIndex = body.indexOf("content=\"", startIdx);
            if (contentStartIndex != -1) {
                int contentStart = contentStartIndex + "content=\"".length();
                if (contentStart != -1) {
                    int contentEnd = body.indexOf("\"", contentStart);
                    if (contentEnd != -1) {
                        String value = body.substring(contentStart, contentEnd);
                        return value;
                    }
                }

            }
        }
        return null;
    }

    /**
     * The document title is in the format "Game Title - Page X" if we're on /giveaways/id/name/search?page=X,
     * so we strip out the page number.
     */
    public static String getPageTitle(Document document) {
        String title = document.title();
        return title.replaceAll(" - Page ([\\d,]+)$", "");
    }

    /**
     * Extracts all images from the description.
     *
     * @param imageHolder item to save this into
     * @param description description of the element
     * @return the description, minus attached images
     */
    public static String loadAttachedImages(IImageHolder imageHolder, Element description) {
        // find all "View attached image" segments
        Elements images = description.select("div > a > img.is-hidden");
        for (Element image : images) {
            // Extract the link.
            String src = image.attr("src");
            if (!TextUtils.isEmpty(src))
                imageHolder.attachImage(new Image(src, image.attr("title")));

            // Remove this image.
            image.parent().parent().html("");
        }

        return description.html();
    }

    public static boolean isConnectedToWifi(final String tag, final Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
        if (activeNetworkInfo == null || !activeNetworkInfo.isConnected() || activeNetworkInfo.getType() != ConnectivityManager.TYPE_WIFI) {
            Log.v(tag, "Not checking for messages due to network info: " + activeNetworkInfo);
            return false;
        }

        return true;
    }
}
