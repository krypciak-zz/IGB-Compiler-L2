package me.krypek.igb.cl2;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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

		System.exit(1);
		if(!quiet)
			for (int i = 0; i < compiled.length; i++) {
				Instruction[] code = compiled[i].code;
				System.out.println("File: " + fileNames[i] + " :");
				for (Instruction element : code)
					System.out.println(element);
				System.out.println("\n");
			}
	}

	static final Set<String> varStr = Set.of("float", "double");

	String[] fileNames;
	// String[][] in;
	int[][] lines;

	int file;
	int line;

	private Functions functions;
	private RAM ram;

	Functions getFunctions() { return functions; }

	RAM getRAM() { return ram; }

	private HashMap<String, Double>[] finalVarsArr;

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

		ram = new RAM(100, 60, 0);
		functions = new Functions(formated, fileNames, ram);
		this.finalVarsArr = functions.finalVarsArr;
		System.out.println(functions);
		System.out.println("\n\n");

		ram.setFinalVariables(finalVarsArr[0]);
		ram.newVar("testvar", new Variable(2137));
		ram.newArray("arrat", new int[] { 2, 4 });
		ram.finalVars.put("finalvar", -2137d);
		System.out.println(Arrays.toString(finalVarsArr));
		return arr;
	}

	private String[][] formatArray(String[] inputs, String[] fileNames) {
		lines = new int[inputs.length][];
		String[][] arr = new String[inputs.length][];
		for (int i = 0; i < inputs.length; i++)
			arr[i] = format(inputs[i], fileNames[i], i);
		return arr;
	}

	private String[] format(String input, String fileName, int index) {
		List<String> list = new ArrayList<>();
		List<Integer> lineList = new ArrayList<>();

		char[] charA = input.toCharArray();
		StringBuilder sb = new StringBuilder(30);

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
			else if(c == '\n')
				line++;
			else if(c == ' ' && (pc == ' ' || wasLastSemi) || c == '\t' || c == '\u000B' || c == '\f' || c == '\r')
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
				sb = new StringBuilder(30);
			} else if(c == '}') {
				if(sb.length() != 0)
					throw new IGB_CL2_Exception("File: \"" + fileName + "\"  Sytnax error");
				list.add("}");
				lineList.add(line);
			} else if(c == ';') {
				if(bracket == 0) {
					if(sb.length() != 0) {
						list.add(sb.toString());
						lineList.add(line);
					}
					sb = new StringBuilder(30);
					wasLastSemi = true;
					continue;
				}
				sb.append(';');

			} else
				sb.append(c);

			wasLastSemi = false;
		}

		lines[index] = lineList.stream().mapToInt(i -> i).toArray();

		return list.toArray(String[]::new);
	}

}
