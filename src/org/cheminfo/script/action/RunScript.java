package org.cheminfo.script.action;

import java.util.HashMap;

import javax.faces.context.FacesContext;
import javax.servlet.ServletContext;

import org.cheminfo.function.scripting.ScriptingInstance;
import org.cheminfo.script.sse.SSECallBack;
import org.cheminfo.script.sse.SSEOutputs;
import org.cheminfo.script.utility.FileTreatment;
import org.cheminfo.script.utility.ScriptInfo;
import org.cheminfo.script.utility.ServletUtilities;
import org.cheminfo.script.utility.Shared;
import org.cheminfo.script.utility.URLFileManager;
import org.json.JSONException;
import org.json.JSONObject;

public class RunScript extends Action {

	private static boolean DEBUG = false;

	final static int FULL_RESULT = 0;
	final static int RESULT_LINK = 1;
	final static int RESULT_ONLY = 2;

	private ScriptInfo scriptInfo;

	public ScriptInfo getScriptInfo() {
		return scriptInfo;
	}

	public void setScriptInfo(ScriptInfo scriptInfo) {
		this.scriptInfo = scriptInfo;
	}

	public void execute() {
		if (homeDir == null) {
			ServletUtilities.returnError(response,
					"Security problem: no home directory - please login!");
		} else {
			try {
				String script = data.getParameterAsString("script");
				String initScript = data.getParameterAsString("initScript");
				String currentDir = data.getParameterAsString("currentDir");

				String resultBranch = data.getParameterAsString("resultBranch");
				String viewBranch = "Master";
				String description = data.getParameterAsString("description");
				boolean getResultBoolean = data
						.getParameterAsBoolean("getResult"); // should we return
																// the result or
																// just save it
																// ?
				int getResult = RESULT_LINK;
				if (getResultBoolean)
					getResult = FULL_RESULT;

				boolean forceNew = true;
				if (data.containsKey("forceNew")) {
					data.getParameterAsBoolean("forceNew");
				}

				runScript(initScript, script, currentDir, resultBranch,
						viewBranch, description, getResult, forceNew);

			} catch (Exception e) {
				ServletUtilities.returnError(response, e.toString());
				e.printStackTrace(System.out);
			}
		}
	}

	public void runScript(String initScript, String script, String currentDir,
			String resultBranch, String viewBranch, String description,
			int getResult, boolean forceNew) throws JSONException {
		if (initScript == null)
			initScript = "";

		if ((script == null) || (script.equals(""))) {
			ServletUtilities.returnError(response,
					"The parameter 'script' must be specified");
		} else {
			ScriptingInstance interpreter = Shared.getScriptingInstance(
					this.data.session, forceNew);

			String sse = "";
			ServletContext context = this.httpServlet.getServletContext();
			if (context.getAttribute("SSE") != null) {
				// sse="Global.SSEToken='"+data.getParameterAsString("SSEToken")+"';"+"console.logCallback=function(value,label){SSEMANAGER.sendLog(value, label, Global.SSEToken);};";
				SSEOutputs logOutputs = ((HashMap<String, SSEOutputs>) context
						.getAttribute("SSE")).get(homeDir);
				// interpreter.addObjectToScope("SSEMANAGER", logOutputs);

				SSECallBack sseCallBack = new SSECallBack(
						data.getParameterAsString("SSEToken"), logOutputs);
				interpreter.getConsole().setCallBack(sseCallBack);
			}

			if (getResult != RESULT_ONLY) {
				Shared.updateHelp(interpreter);
			}
			String dataFolder = Shared.getProperty("DATA_FOLDER", null);

			if (dataFolder != null) {
				if (DEBUG)
					System.out.println("RunScript: DATA_FOLDER: " + dataFolder);
				// In the script we could add some global variable

				if (DEBUG)
					System.out.println("RunScript: homeDir: " + homeDir);

				interpreter.setSafePath(homeDir);

				script = ""
						+
						// "Global.readKey='"+URLFileManager.getFileKey(homeDir,
						// false)+"';"+
						// "Global.writeKey='"+URLFileManager.getFileKey(homeDir,
						// true)+"';"+
						"Global.baseURL='"
						+ this.data.request.getRequestURL()
						+ "';"
						+ "Global.basedir='"
						+ homeDir
						+ "';"
						+ "Global.currentDir='"
						+ currentDir
						+ "';"
						+ "Global.serverURL='"
						+ this.data.getServerURL()
						+ "';"
						+ sse
						+ "function getReadFileURL(filename) { return URLAccessHelper.getReadFileURL(Global.basedir, Global.basedirkey, File.checkGlobal(filename), Global.baseURL)+'';};"
						+ "function getWriteFileURL(filename) { return URLAccessHelper.getWriteFileURL(Global.basedir, Global.basedirkey, File.checkGlobal(filename), Global.baseURL)+'';};"
						+ "var File=File || {};"
						+ "File.getReadURL=function(filename) { return getReadFileURL(filename); };"
						+ "File.getWriteURL=function(filename) { return getWriteFileURL(filename); };"
						+ "File.getLoginURL=function(foldername) { return URLAccessHelper.getLoginURL(Global.basedir, Global.basedirkey, File.checkGlobal(foldername), Global.baseURL)+''; };"
						+ initScript + script;
			} else {
				if (DEBUG)
					System.out.println("RunScript: DATA_FOLDER is null");
			}

			if (DEBUG)
				System.out.println("RunScript: script value: " + script);

			JSONObject toReturn = interpreter.runScript(script);
			System.out.println(toReturn);
			if (getResult != RESULT_ONLY) {

				try {
					if (DEBUG)
						System.out.println("RunScript: currentdir: "
								+ currentDir);

					String viewFilename = FileTreatment.getLastFilename(
							homeDir, currentDir, ".views", viewBranch, "",
							"json");
					FileTreatment.createFolder(viewFilename.replaceAll(
							"[^/]*$", ""));
					if (viewFilename != null) {
						toReturn.put("_viewFilename",
								viewFilename.replaceFirst(homeDir, ""));
						String vueURL=
								 URLFileManager.getFileReadURL(viewFilename,
										 data.request.getRequestURL().toString());
						 toReturn.put("_viewUrl",vueURL);
						 scriptInfo.setViewURL(vueURL);
					}
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace(System.out);
				}

				String dataFilename = null;
				try {
					dataFilename = FileTreatment.getNewFilename(homeDir,
							currentDir, ".results", resultBranch, description,
							"json");
					if (dataFilename != null) {
						toReturn.put("_dataFilename",
								dataFilename.replaceFirst(homeDir, ""));
						// required to see the full data as json
						String dataURL= URLFileManager.getFileReadURL(
								dataFilename, data.request.getRequestURL()
								.toString());
						toReturn.put("_dataUrl",dataURL);
						scriptInfo.setDataURL(dataURL);
					}
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace(System.out);
				}

				// toReturn.put("title", description);
				toReturn.put("_saved", System.currentTimeMillis());
				// toReturn.put("_time",
				// Math.abs(System.currentTimeMillis()/1000));

				if ((dataFilename != null) && (!dataFilename.equals(""))) {
					FileTreatment.save(toReturn.toString(), dataFilename);
					// ()(((HashMap<String, CircularFifoQueue<ScriptInfo>>)
					

					if (getResult == RESULT_LINK) {
						// we remove all that is not protected
						for (String key : JSONObject.getNames(toReturn)) {
							if (!key.startsWith("_")) {
								toReturn.remove(key);
							}
						}
					}
				}

				ServletUtilities.returnResponse(response, toReturn.toString(),
						"application/json");
			}
		}
	}

}
