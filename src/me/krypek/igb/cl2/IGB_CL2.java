package me.krypek.igb.cl2;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.krypek.freeargparser.ArgType;
import me.krypek.freeargparser.ParsedData;
import me.krypek.freeargparser.ParserBuilder;
import me.krypek.igb.cl1.IGB_L1;
import me.krypek.igb.cl1.Instruction;
import me.krypek.utils.Utils;

public class IGB_CL2 {

	@SuppressWarnings("unused")
	public static void main(String[] args) {
		//@f:off
		ParsedData data = new ParserBuilder()
				.add("cp", "codepath", 		true,	false, 	ArgType.StringArray, 	"Array of paths to code files.")
				.add("op", "outputpath", 	false,	false, 	ArgType.String, 		"Path to a directory, l1 files will be saved here.")
				.add("ro", "readableOutput",false, 	false,	ArgType.None, 			"If selected, will also save readable l1 files.")
				.add("q", "quiet", 			false,	false,	ArgType.None, 			"If selected, won't print output to terminal.")
				.parse(args);
		//@f:on

		final String[] codePaths = data.getStringArray("cp");
		final String outputPath = data.getStringOrDef("op", null);
		final boolean readableOutput = data.has("ro");
		final boolean quiet = data.has("q");

		String[] inputs = new String[codePaths.length];
		String[] fileNames = new String[codePaths.length];
		for (int i = 0; i < inputs.length; i++) {
			String readden = Utils.readFromFile(codePaths[i], "\n");
			if(readden == null)
				throw new IGB_CL2_Exception("Error reading file: \"" + codePaths[i] + "\".");
			inputs[i] = readden;

			fileNames[i] = new File(codePaths[i]).getName();
		}

		{
			Set<String> duplicate = new HashSet<>();
			for (String name : fileNames) {
				if(duplicate.contains(name))
					throw new IGB_CL2_Exception("Duplicate file names aren't supported: \"" + name + "\".");
				duplicate.add(name);
			}
		}

		IGB_CL2 igb_cl2 = new IGB_CL2();
		IGB_L1[] compiled = igb_cl2.compile(inputs, fileNames);

		if(!quiet)
			for (int i = 0; i < compiled.length; i++) {
				Instruction[] code = compiled[i].code;
				System.out.println("File: " + fileNames[i] + " ->");
				for (Instruction element : code)
					System.out.println(element);
				System.out.println("\n");
			}
	}

	static final Set<String> varStr = Set.of("float", "double", "int");

	String[] fileNames;
	// String[][] in;
	int[][] lines;

	int file;
	int line;

	private Functions functions;
	private RAM ram;

	Functions getFunctions() { return functions; }

	RAM getRAM() { return ram; }

	private VarSolver varsolver;

	@SuppressWarnings("unused")
	private boolean assu;

	public IGB_CL2() { IGB_CL2_Exception.igb_cl2 = this; }

	public IGB_L1[] compile(String[] inputs, String[] fileNames) {
		IGB_L1[] arr = new IGB_L1[inputs.length];
		String[][] formated = formatArray(inputs, fileNames);

		this.fileNames = fileNames;
		// log
		for (int i = 0; i < formated.length; i++) {
			System.out.println(fileNames[i] + " ->");
			for (int x = 0; x < formated[i].length; x++)
				System.out.println("  " + (lines[i][x] + 1) + ": " + formated[i][x]);
		}
		// endlog

		functions = new Functions(formated, fileNames);
		System.out.println(functions);
		System.out.println("\n\n");

		for (int i = 0; i < formated.length; i++) {
			ArrayList<Instruction> instList = new ArrayList<>();
			ram = functions.rams[i];
			varsolver = new VarSolver(this);
			for (int x = 0; x < formated[i].length; x++) {
				String cmd = formated[i][x];
				ArrayList<Instruction> out = cmd(cmd);
				if(out == null) {
					// return;
					instList.add(Instruction.Pointer(":null"));
				} else
					instList.addAll(out);
			}
			if(instList.size() > functions.lenlimits[i])
				throw new IGB_CL2_Exception(true,
						"\nFile: " + fileNames[i] + " Instruction length limit reached: " + instList.size() + " out of " + functions.lenlimits[i] + ".");

			arr[i] = new IGB_L1(functions.startlines[i], instList.toArray(Instruction[]::new));
		}

		return arr;
	}

	private ArrayList<Instruction> cmd(String cmd) {

		{
			ArrayList<Instruction> var = varsolver.cmd(cmd);
			if(var != null)
				return var;
		}

		return null;
	}

	private String[][] formatArray(String[] inputs, String[] fileNames) {
		lines = new int[inputs.length][];
		String[][] arr = new String[inputs.length][];
		for (int i = 0; i < inputs.length; i++)
			arr[i] = format(inputs[i], fileNames[i], i);
		return arr;
	}

	private String[] format(String input, String fileName, int index) {
		final int stringBuilderSize = 30;
		List<String> list = new ArrayList<>();
		List<Integer> lineList = new ArrayList<>();

		char[] charA = input.toCharArray();
		StringBuilder sb = new StringBuilder(stringBuilderSize);

		boolean isQuote = false, isQuote1 = false, wasLastSemi = false;
		int bracket = 0, line = 0;

		for (int i = 0; i < charA.length; i++) {
			char c = charA[i];
			char pc = i == 0 ? '?' : charA[i - 1];

			if(c == '"' && pc != '\\')
				isQuote = !isQuote;
			else if(c == '\'' && pc != '\\')
				isQuote1 = !isQuote1;
			else if(isQuote || isQuote1)
				sb.append(c);
			else if(c == '\n') {
				line++;
				if(sb.length() >= 2 && sb.charAt(0) == '/' && sb.charAt(1) == '/')
					sb = new StringBuilder(stringBuilderSize);

			} else if(c == ' ' && (pc == ' ' || wasLastSemi) || c == '\t' || c == '\u000B' || c == '\f' || c == '\r')
				continue;
			else if(c == '(') {
				sb.append('(');
				bracket++;
			} else if(c == ')') {
				bracket--;
				sb.append(')');
			} else if(c == '{') {
				if(sb.length() != 0) {
					list.add(sb.toString());
					lineList.add(line);
				}
				list.add("{");
				lineList.add(line);
				sb = new StringBuilder(stringBuilderSize);
			} else if(c == '}') {
				if(sb.length() != 0)
					throw new IGB_CL2_Exception(fileName, line, "Sytnax error");
				list.add("}");
				lineList.add(line);
			} else if(c == ';') {
				if(sb.length() >= 2 && sb.charAt(0) == '/' && sb.charAt(1) == '/') {
					sb = new StringBuilder(stringBuilderSize);
					sb.append("//");
				} else {
					if(bracket == 0) {
						if(sb.length() != 0) {
							list.add(sb.toString());
							lineList.add(line);
						}
						sb = new StringBuilder(stringBuilderSize);
						wasLastSemi = true;
						continue;
					}
					sb.append(';');
				}
			} else
				sb.append(c);

			wasLastSemi = false;
		}

		lines[index] = lineList.stream().mapToInt(i -> i).toArray();

		return list.toArray(String[]::new);
	}

}
