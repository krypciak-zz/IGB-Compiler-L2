package me.krypek.igb.cl2;

import java.io.File;
import java.util.ArrayList;

import me.krypek.freeargparser.ArgType;
import me.krypek.freeargparser.ParsedData;
import me.krypek.freeargparser.ParserBuilder;
import me.krypek.igb.cl1.datatypes.IGB_L1;
import me.krypek.igb.cl1.datatypes.Instruction;
import me.krypek.igb.cl2.IGB_CL2_Exception.Err;
import me.krypek.igb.cl2.solvers.ControlSolver;
import me.krypek.igb.cl2.solvers.EqSolver;
import me.krypek.igb.cl2.solvers.VarSolver;
import me.krypek.utils.Utils;

public class IGB_CL2 {

	public static int toStringTabs = 0;

	public static String getTabs() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < toStringTabs; i++)
			sb.append("\t");
		return sb.toString();
	}

	public static void main(String[] args) {
		//@f:off
		ParsedData data = new ParserBuilder()
				.add("cp", "codepath", 		true,	false, 	ArgType.String, 		"The path to the main file.")
				.add("op", "outputpath", 	false,	false, 	ArgType.String, 		"Path to a directory, l1 files will be saved here.")
				.add("ro", "readableOutput",false, 	false,	ArgType.None, 			"If selected, will also save readable l1 files.")
				.add("q", "quiet", 			false,	false,	ArgType.None, 			"If selected, won't print output to terminal.")
				.parse(args);
		//@f:on

		final String mainPath = data.getString("cp");
		final String outputPath = data.getStringOrDef("op", null);
		final boolean readableOutput = data.has("ro");
		final boolean quiet = data.has("q");

		IGB_CL2 igb_cl2 = new IGB_CL2(quiet);
		IGB_L1[] compiled = igb_cl2.compile(mainPath);

		if(outputPath != null) {
			File outDir = new File(outputPath);
			outDir.mkdirs();
			for (int i = 0; i < compiled.length; i++) {
				IGB_L1 l1 = compiled[i];
				String fileName = Utils.getFileNameWithoutExtension(compiled[i].path());
				Utils.serialize(l1, outputPath + File.separator + fileName + ".igb_l1");
				if(readableOutput)
					Utils.writeIntoFile(outputPath + File.separator + fileName + ".igb_l1_readable", l1.toString());
			}
		}

	}

	private final boolean quiet;

	public PrecompilationFile[] precfA;
	public int file;
	public int line;
	private boolean befFirstCtrlInstructionsAppended;
	private ArrayList<Instruction> befFirstFuncInstructions;

	public IGB_CL2(boolean quiet) { this.quiet = quiet; }

	public IGB_L1[] compile(String mainPath) {
		System.out.println("\n-----IGB CL2----------------------------------------------------------\n");

		Precompilation prec = new Precompilation(mainPath);
		Functions functions = prec.functions;
		precfA = prec.precfA;

		IGB_L1[] arr = new IGB_L1[precfA.length];

		for (file = 0; file < precfA.length; file++) {
			PrecompilationFile precf = precfA[file];
			befFirstFuncInstructions = precf.befFirstFuncInstructions;
			befFirstCtrlInstructionsAppended = false;

			ArrayList<Instruction> instList = new ArrayList<>();
			RAM ram = precf.ram;
			EqSolver eqsolver = new EqSolver(ram, functions);
			VarSolver varsolver = new VarSolver(eqsolver, ram);
			ControlSolver cntrlsolver = new ControlSolver(functions, varsolver, eqsolver, ram, precf.cmd);

			for (line = 0; line < precf.cmd.length; line++) {
				Err.updateLine(precf.lines[line]);

				String cmd = precf.cmd[line];

				try {
					ArrayList<Instruction> out = cmd(cmd, varsolver, cntrlsolver);
					if(out == null)
						throw Err.normal("Unknown command: \"" + cmd + "\".");
					instList.addAll(out);

				} catch (Exception e) {
					System.out.println(this);
					throw e;
				}

				// System.out.println("cmd: " + cmd + " -> " + out);

			}
			cntrlsolver.checkStack();

			if(instList.size() > precf.lenlimit)
				throw Err.noLine("Instruction length limit reached: " + instList.size() + " out of " + precf.lenlimit + ".");

			arr[file] = new IGB_L1(precf.startline, instList.toArray(Instruction[]::new), precf.fileName, precf.path);
		}

		if(!quiet)
			for (file = 0; file < arr.length; file++) {

				Instruction[] code = arr[file].code();
				if(arr[file].path().startsWith("$res")) {
					System.out.println("Library: " + precfA[file].fileName + "   len: " + code.length);
					continue;
				}
				System.out.println("File: " + precfA[file].fileName + "   len: " + code.length + " ->");

				for (Instruction element : code)
					System.out.println(element);
				System.out.println("\n");
			}
		System.out.println("\n");
		System.out.println(this);
		return arr;
	}

	private ArrayList<Instruction> cmd(String cmd, VarSolver varsolver, ControlSolver cntrlsolver) {
		if(cmd.length() > 0 && cmd.charAt(0) == '$' || cmd.startsWith("final"))
			return new ArrayList<>();

		{
			ArrayList<Instruction> var = varsolver.cmd(cmd, false);
			if(var != null)
				return var;
		}
		{
			ArrayList<Instruction> cntrl = cntrlsolver.cmd(cmd, line);
			if(cntrl != null) {
				if(!befFirstCtrlInstructionsAppended) {
					befFirstCtrlInstructionsAppended = true;
					ArrayList<Instruction> t0 = new ArrayList<>(befFirstFuncInstructions);
					t0.addAll(cntrl);
					return t0;
				}
				return cntrl;
			}
		}

		return null;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(IGB_CL2.getTabs() + "IGB_CL2: {\n");
		IGB_CL2.toStringTabs++;
		sb.append(precfA[0].functions.toString());
		for (PrecompilationFile precf : precfA) {
			if(precf.path.startsWith("$res"))
				continue;
			sb.append(precf.toString() + "\n");
		}
		IGB_CL2.toStringTabs--;
		sb.append(IGB_CL2.getTabs() + "\n}");
		return sb.toString();
	}

}
