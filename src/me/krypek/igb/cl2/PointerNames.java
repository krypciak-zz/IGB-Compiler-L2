package me.krypek.igb.cl2;

public class PointerNames {
	public static String functionStart(String name, int arglen) { return ":f_" + name + "_" + arglen + "_start"; }

	public static String functionEnd(String name, int arglen) { return ":f_" + name + "_" + arglen + "_end"; }

	public static String elseIf(int index) { return ":elseif_" + index; }

	public static String bracketStart(int index) { return ":bracket_" + index + "_start"; }

	public static String bracketEnd(int index) { return ":bracket_" + index + "_end"; }

	public static String forStart(int index) { return ":for_" + index + "_start"; }

	public static String forEnd(int index) { return ":for_" + index + "_end"; }

	public static String forCheck(int index) { return ":for_" + index + "_check"; }

	public static String forLoop(int index) { return ":for_" + index + "_loop"; }

	public static String whileStart(int index) { return ":while_" + index + "_start"; }

	public static String whileEnd(int index) { return ":while_" + index + "_end"; }

	public static String whileLoop(int index) { return ":while_" + index + "_loop"; }

	public static String whileCheck(int index) { return ":while_" + index + "_check"; }

	public static String ifStart(int index) { return ":if_" + index + "_start"; }

	public static String ifEnd(int index) { return ":if_" + index + "_end"; }
}
