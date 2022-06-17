package me.krypek.igb.cl2;

public class IGB_CL2_Exception extends RuntimeException {

	public static class Err {
		static public boolean showFilePath = false;

		static private String fileName;
		static private String path;

		static private int line;

		static public void updateFile(String fileName, String path) {
			Err.fileName = fileName;
			Err.path = path;
		}

		public static void updateLine(int line) { Err.line = line; }

		private static String getFile() { return showFilePath ? path : fileName; }

		private static String getFileString() { return "\nFile: \"" + getFile() + "\"\n"; }

		private static String getLineString() { return "Line: " + line + "\n"; }

		public static IGB_CL2_Exception raw(String str) { return new IGB_CL2_Exception(str); }

		public static IGB_CL2_Exception noLine(String str) { return new IGB_CL2_Exception(getFileString() + str); }

		public static IGB_CL2_Exception normal(String str) { return new IGB_CL2_Exception(getFileString() + getLineString() + str); }

		public static IGB_CL2_Exception normal(String str, Exception e) { return new IGB_CL2_Exception(getFileString() + getLineString() + str, e); }

		public static IGB_CL2_Exception notPossible() { return new IGB_CL2_Exception(getFileString() + getLineString() + "dat not possible"); }
	}

	private static final long serialVersionUID = -3981383146499528214L;

	private IGB_CL2_Exception(String str) { super(str); }

	private IGB_CL2_Exception(String str, Exception e) { super(str, e); }

}
