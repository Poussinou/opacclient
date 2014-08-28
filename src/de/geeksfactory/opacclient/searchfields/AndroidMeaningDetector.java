package de.geeksfactory.opacclient.searchfields;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.res.AssetManager;
import de.geeksfactory.opacclient.objects.Library;
import de.geeksfactory.opacclient.searchfields.SearchField.Meaning;

public class AndroidMeaningDetector implements MeaningDetector {

	private Map<String, String> meanings;
	private AssetManager assets;

	private static final String ASSETS_FIELDSDIR = "meanings";

	public AndroidMeaningDetector(Context context, Library lib)
			throws IOException, JSONException {
		meanings = new HashMap<String, String>();
		assets = context.getAssets();
		List<String> files = Arrays.asList(assets.list(ASSETS_FIELDSDIR));
		if (files.contains("general.json")) // General
			readFile("general.json");
		if (files.contains(lib.getApi() + ".json")) // Api specific
			readFile(lib.getApi() + ".json");
		if (files.contains(lib.getIdent() + ".json")) // Library specific
			readFile(lib.getIdent() + ".json");
	}

	private void readFile(String name) throws IOException, JSONException {
		InputStream fis = assets.open(ASSETS_FIELDSDIR + "/" + name);

		BufferedReader reader = new BufferedReader(new InputStreamReader(fis,
				"utf-8"));
		String line;
		StringBuilder builder = new StringBuilder();
		while ((line = reader.readLine()) != null) {
			builder.append(line);
		}

		fis.close();
		JSONObject json = new JSONObject(builder.toString());

		// Detect layout of the JSON entries. Can be "field name":
		// "meaning" or "meaning": [ "field name", "field name", ... ]
		Iterator<String> iter = json.keys();
		if (!iter.hasNext())
			return; // No entries

		String firstKey = iter.next();
		Object firstValue = json.get(firstKey);
		boolean arrayLayout = firstValue instanceof JSONArray;
		if (arrayLayout) {
			for (int i = 0; i < ((JSONArray) firstValue).length(); i++)
				meanings.put(((JSONArray) firstValue).getString(i), firstKey);
			while (iter.hasNext()) {
				String key = iter.next();
				JSONArray val = json.getJSONArray(key);
				for (int i = 0; i < val.length(); i++)
					meanings.put(val.getString(i), key);
			}
		} else {
			meanings.put(firstKey, (String) firstValue);
			while (iter.hasNext()) {
				String key = iter.next();
				String val = json.getString(key);
				meanings.put(key, val);
			}
		}
	}

	@Override
	public SearchField detectMeaning(SearchField field) {
		for (Entry<String, String> entry : meanings.entrySet()) {
			if (field.getDisplayName().contains(entry.getKey())) {
				Meaning meaning = Meaning.valueOf(entry.getValue());
				if (field instanceof TextSearchField && meaning == Meaning.FREE) {
					((TextSearchField) field).setFreeSearch(true);
					((TextSearchField) field).setHint(field.getDisplayName());
				} else if (field instanceof TextSearchField
						&& (meaning == Meaning.BARCODE || meaning == Meaning.ISBN)) {
					field = new BarcodeSearchField(field.getId(),
							field.getDisplayName(), field.isAdvanced(),
							((TextSearchField) field).isHalfWidth(),
							((TextSearchField) field).getHint());
				} else if (meaning == Meaning.AUDIENCE
						|| meaning == Meaning.SYSTEM
						|| meaning == Meaning.KEYWORD
						|| meaning == Meaning.PUBLISHER) {
					field.setAdvanced(true);
				}
				field.setMeaning(meaning);
				return field;
			}
		}
		field.setAdvanced(true);
		return field;
	}

}
