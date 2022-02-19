package me.krypek.igb.cl2;

import java.util.ArrayList;
import java.util.Arrays;

import me.krypek.igb.cl1.Instruction;
import me.krypek.utils.Utils;

public class VarSolver {

	private final IGB_CL2 cl2;
	private final RAM ram;

	public VarSolver(IGB_CL2 cl2) {
		this.cl2 = cl2;
		this.ram = cl2.getRAM();
	}

	public ArrayList<Instruction> cmd(String cmd) {
		ArrayList<Instruction> list = new ArrayList<>();

		for (char c : EqSolver.operators) {
			int index = cmd.indexOf(c + "=");
			if(index != -1) {
				String name = cmd.substring(0, index).strip();
				int cell = ram.getVariable(name);
				String eq = cmd.substring(index + 2).strip();
				return cl2.getEqSolver().solve(name + c + "(" + eq + ")", cell);
			}
		}

		int spaceIndex = cmd.indexOf(' ');
		if(spaceIndex != -1) {
			String first = cmd.substring(0, spaceIndex);
			if(IGB_CL2.varStr.contains(first)) {
				// var init
				String rest = cmd.substring(spaceIndex + 1).strip();
				int equalsIndex = rest.indexOf('=');
				if(equalsIndex == -1) {
					if(rest.contains("(") || rest.contains(")"))
						return null;
					ram.newVar(rest);
					return new ArrayList<>();
				} else {
					String name = rest.substring(0, equalsIndex).strip();
					String eq = rest.substring(equalsIndex + 1).strip();
					int cell = ram.reserve(1)[0];
					list.addAll(cl2.getEqSolver().solve(eq, cell));
					ram.newVar(name, new Variable(cell));
					return list;
				}
			} else {
				int bracketIndex = cmd.indexOf('[');
				if(bracketIndex != -1) {
					initArray(cmd, bracketIndex);
					return new ArrayList<>();
				}
			}
		}

		return null;
	}

	private void initArray(String cmd, int bracketIndex) {
		String type = cmd.substring(0, bracketIndex);
		if(!IGB_CL2.varStr.contains(type))
			throw new IGB_CL2_Exception("Unknown variable type: \"" + type + "\".");

		String dims = cmd.substring(bracketIndex, cmd.length());
		String[] split = dims.split("=");
		if(split.length == 1)
			throw new IGB_CL2_Exception("Array needs to be initalized.");
		if(split.length > 2)
			throw new IGB_CL2_Exception("Syntax error.");
		String eq = split[1].strip();
		int lastBracketIndex = split[0].lastIndexOf(']');
		if(lastBracketIndex == -1)
			throw new IGB_CL2_Exception("Syntax error.");
		String name = split[0].substring(lastBracketIndex + 1, split[0].length()).strip();
		if(name.contains("["))
			throw new IGB_CL2_Exception("Bracket syntax error.");

		dims = split[0].substring(0, lastBracketIndex + 1);
		int dimsCount = 0;

		for (char c : dims.toCharArray()) {
			if(Character.isWhitespace(c))
				continue;
			int t1 = dimsCount++ % 2;
			if((c == '[' && t1 == 1) || (c == ']' && t1 == 0))
				throw new IGB_CL2_Exception("Bracket syntax error.");
		}
		if(dimsCount % 2 == 1)
			throw new IGB_CL2_Exception("Please enter a valid amount of brackets.");
		dimsCount /= 2;

		for (String str : IGB_CL2.varStr) {
			final String str1 = "new " + str;

			if(eq.startsWith(str1)) {
				String dims1 = eq.substring(str1.length());

				int[] size = Arrays.stream(Utils.getArrayElementsFromString(dims1, (str2) -> {
					double val = ram.solveFinalEq(str2);
					if(val % 1 != 0)
						throw new IGB_CL2_Exception("Array size has to be an integer.");
					if(val < 0)
						throw new IGB_CL2_Exception("Array size cannot be negative.");
					return (int) val;
				}, new Integer[0], '[', ']', new IGB_CL2_Exception("Array size bracket syntax error."))).mapToInt(Integer::intValue).toArray();
				if(size.length != dimsCount)
					throw new IGB_CL2_Exception("Expected " + dimsCount + " dimenstions, insted got " + size.length + ".");

				ram.newArray(name, size);
				return;
			}
		}
		throw new IGB_CL2_Exception("Invalid array initalizer: \"" + eq + "\".");
	}

}
