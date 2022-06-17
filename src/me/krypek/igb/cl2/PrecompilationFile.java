package me.krypek.igb.cl2;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import me.krypek.igb.cl1.Instruction;
import me.krypek.igb.cl2.IGB_CL2_Exception.Err;
import me.krypek.igb.cl2.datatypes.function.FunctionNormalField;
import me.krypek.igb.cl2.datatypes.function.UserFunction;
import me.krypek.igb.cl2.solvers.VarSolver;
import me.krypek.utils.Pair;
import me.krypek.utils.Utils;
import static me.krypek.igb.cl1.Instruction.*;

public class PrecompilationFile {
	private static int DEFAULT_RAMLIMIT = 100;
	private static int DEFAULT_THREAD = 0;

	private static final Map<String, String> libMap = Map.of("charlib", "$res charlib.igb_l2");

	private final Functions functions;
	private UserFunction main;
	public RAM ram;
	public int startline = -1;
	public int lenlimit = -1;
	public int ramcell = -1;
	public int ramlimit = -1;
	public int thread = -1;
	public LinkedHashSet<String> dependencies;
	public ArrayList<Instruction> startInstructions;

	public final String[] cmd;
	public final int[] lines;
	public final String path;
	public final String fileName;

	public PrecompilationFile(String path, Functions functions) {
		this.functions = functions;
		this.path = path;
		this.fileName = Utils.getFileName(path);
		dependencies = new LinkedHashSet<>();
		startInstructions = new ArrayList<>();

		Err.updateFile(fileName, path);

		String contents = readFromFile(path);

		var formatPair = format(contents);
		cmd = formatPair.getFirst();
		lines = formatPair.getSecond();

		for (int i = 0; i < cmd.length; i++) { Err.updateLine(i); processCmd(cmd[i]); }
	}

	private String readFromFile(String path) {
		String str = Utils.readFromFile(path, "\n");
		if(str == null)
			throw Err.normal("Error reading file: \"" + path + "\".");
		return str;
	}

	private void processCmd(String cmd) {
		int spaceIndex = cmd.indexOf(' ');
		if(spaceIndex == -1)
			spaceIndex = cmd.length();
		String first = cmd.substring(0, spaceIndex);
		if(spaceIndex == cmd.length())
			spaceIndex--;
		if(cmd.startsWith("$"))
			_compiler(cmd);
		else {
			if(ram == null)
				initRAM();
			assert ram != null;
			assert startline != -1;

			String rest = cmd.substring(spaceIndex + 1).strip();
			if(first.equals("void"))
				_function(false, rest);
			else if(VarSolver.typeSet.contains(first) && !cmd.contains("=") && cmd.contains("("))
				_function(true, rest);
			else if(first.equals("final"))
				_finalvar(rest);
			else if(first.equals("#include"))
				_include(rest);
		}
	}

	private void _include(String filePath) {
		filePath = filePath.strip();
		if(filePath.startsWith("<") && filePath.endsWith(">")) {
			String libName = filePath.substring(0, filePath.length() - 1);
			String libPath = libMap.get(libName);
			if(libPath == null)
				throw Err.normal("Library <" + libName + "> doesn't exist.");

			dependencies.add(libPath);
			return;
		}

		File file = new File(filePath);
		if(!file.exists())
			throw Err.normal("File: \"" + file.getAbsolutePath() + "\" doesn't exist.");

		dependencies.add(file.getAbsolutePath());
	}

	private void _function(boolean returnType, String rest) {
		int bracketIndex = rest.indexOf('(');
		String name = rest.substring(0, bracketIndex);
		String[] splited = Utils.getArrayElementsFromString(rest.substring(bracketIndex), '(', ')', ",");

		FunctionNormalField[] fields = new FunctionNormalField[splited.length];
		int[] cells = ram.reserve(splited.length);

		for (int i = 0; i < splited.length; i++) {
			String arg = splited[i];
			int spaceIndex = arg.indexOf(' ');
			if(spaceIndex == -1)
				throw Err.normal("Function syntax Error.");

			String type = arg.substring(0, spaceIndex);
			if(!VarSolver.typeSet.contains(type))
				throw Err.normal("Invalid type: \"" + type + "\".");
			String argName = arg.substring(spaceIndex + 1);
			fields[i] = new FunctionNormalField(argName, cells[i]);
		}
		int argsLen = splited.length;
		String startPointer = PointerNames.functionStart(name, argsLen);
		String endPointer = PointerNames.functionEnd(name, argsLen);

		UserFunction func = new UserFunction(name, startPointer, endPointer, fields, returnType);
		functions.addFunction(func);

		if(name.equals("main")) {
			if(argsLen != 0)
				throw Err.normal("Main function has to have 0 arguments.");
			main = func;
			startInstructions.add(Cell_Call(main.startPointer));
			startInstructions.add(Cell_Jump(-2));
		}
	}

	private void _finalvar(String rest) {
		if(!rest.contains("="))
			throw Err.normal("Final variable has to be set.");

		String[] split = rest.split("=");
		if(split.length != 2)
			throw Err.normal("Syntax Error.");
		String eq = split[1].strip();
		int spaceIndex = split[0].indexOf(' ');
		if(spaceIndex == -1)
			throw Err.normal("Syntax Error.");

		split[0] = split[0].stripLeading();
		String varname = split[0].substring(spaceIndex).strip();
		String type = split[0].substring(0, spaceIndex + 1).strip();

		if(!VarSolver.typeSet.contains(type))
			throw Err.normal("Unknown variable type: \"" + type + "\".");
		double val = RAM.solveFinalEq(eq, ram.finalVars);
		if(ram.finalVars.containsKey(varname))
			throw Err.normal("Final variable already exists: \"" + varname + "\".");
		ram.finalVars.put(varname, val);
	}

	private void initRAM() {
		if(startline == -1)
			throw Err.normal("Startline has to be set in a main file.");

		if(lenlimit == -1)
			lenlimit = Integer.MAX_VALUE;

		if(ramcell == -1)
			throw Err.normal("Startline has to be set in a main file.");

		if(thread == -1)
			thread = DEFAULT_THREAD;

		if(ramlimit == -1)
			ramlimit = DEFAULT_RAMLIMIT;

		ram = new RAM(ramlimit, ramcell, thread);
	}

	private void _compiler(String cmd) {
		String[] split = cmd.split("=");
		if(split.length == 1)
			throw Err.normal("Compiler variable has to be set.");
		if(split.length > 2)
			throw Err.normal("Syntax Error.");
		String eq = split[1].strip();

		String name = split[0].substring(1).strip();

		double valD = RAM.solveFinalEq(eq, new HashMap<>());
		if(valD % 1 != 0)
			throw Err.normal("Compiler variable value has to be an integer.");
		int val = (int) valD;
		if(val < 0)
			throw Err.normal("Compiler variable value cannot be negative.");

		switch (name.toLowerCase()) {
		case "startline" -> {
			if(startline != -1)
				throw Err.normal("Cannot set startline twice.");
			startline = val;
		}
		case "lenlimit" -> {
			if(lenlimit != -1)
				throw Err.normal("Cannot set lenlimit twice.");
			lenlimit = val;
		}
		case "ramlimit" -> {
			if(ramlimit != -1)
				throw Err.normal("Cannot set ramlimit twice.");
			ramlimit = val;
		}
		case "ramcell" -> {
			if(ramcell != -1)
				throw Err.normal("Cannot set ramcell twice.");
			ramcell = val;
		}
		case "thread" -> {
			if(thread > 1)
				throw Err.normal("Thread can be only 0 or 1.");
		}
		default -> throw Err.normal("Unknown compiler variable: \"" + name + "\".");
		}
	}

	private Pair<String[], int[]> format(String input) {
		final int stringBuilderSize = 30;
		List<String> list = new ArrayList<>();
		List<Integer> lineList = new ArrayList<>();

		char[] charA = input.toCharArray();
		StringBuilder sb = new StringBuilder(stringBuilderSize);

		boolean isQuote = false, wasLastSemi = false;
		int bracket = 0, line = 0;

		Err.updateLine(0);

		for (int i = 0; i < charA.length; i++) {
			char c = charA[i];
			char pc = i == 0 ? '?' : charA[i - 1];

			if(!isQuote && c == '/' && pc == '/') {
				sb = new StringBuilder(stringBuilderSize);
				for (; charA[i] != '\n'; i++) {}
				continue;
			}

			if(c == '"' && pc != '\\') {
				isQuote = !isQuote;
				sb.append(c);
			} else if(!isQuote && c == '\'' && pc != '\\') {
				if(charA[i + 2] == '\'') {
					sb.append((int) charA[i + 1]);
					i += 2;
				}
			} else if(isQuote)
				sb.append(c);
			else if(c == '\n') {
				Err.updateLine(++line);
			} else if(c == ' ' && (pc == ' ' || wasLastSemi) || (c == ' ' && sb.isEmpty()) || (c != ' ' && Character.isWhitespace(c)))
				continue;
			else if(c == '(') {
				sb.append('(');
				bracket++;
			} else if(c == ')') {
				bracket--;
				sb.append(')');
			} else if(c == '{') {
				if(!sb.isEmpty()) {

					list.add(sb.toString());
					lineList.add(line);

				}
				list.add("{");
				lineList.add(line);
				sb = new StringBuilder(stringBuilderSize);
			} else if(c == '}') {
				if(!sb.isEmpty())
					throw Err.normal("Sytnax Error (DEBUG INFO: \"" + sb.toString() + "\")");
				list.add("}");
				lineList.add(line);
			} else if(c == ';') {
				if(bracket == 0) {
					if(!sb.isEmpty()) {
						list.add(sb.toString());
						lineList.add(line);
					}
					sb = new StringBuilder(stringBuilderSize);
					wasLastSemi = true;
					continue;
				}
				sb.append(';');
			} else
				sb.append(c);

			wasLastSemi = false;
		}

		return new Pair<String[], int[]>(list.toArray(String[]::new), lineList.stream().mapToInt(i -> i).toArray());
	}
}
