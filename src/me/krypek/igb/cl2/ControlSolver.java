package me.krypek.igb.cl2;

import static me.krypek.igb.cl1.Instruction.Cell_Jump;
import static me.krypek.igb.cl1.Instruction.Cell_Return;
import static me.krypek.igb.cl1.Instruction.Pointer;
import static me.krypek.igb.cl2.BracketType.Bracket;
import static me.krypek.igb.cl2.BracketType.Default;
import static me.krypek.igb.cl2.BracketType.Function;
import static me.krypek.igb.cl2.BracketType.*;
import static me.krypek.igb.cl2.ControlSolver.Bracket._default;
import static me.krypek.igb.cl2.ControlSolver.Bracket.bracketIndex;

import java.util.ArrayList;
import java.util.Set;
import java.util.Stack;

import me.krypek.igb.cl1.Instruction;
import me.krypek.utils.Utils;

enum BracketType {
	Default, Bracket, If, For, While, Function
}

class ControlSolver {
	static class Bracket {
		static int bracketIndex = 0;
		private static int forIndex = 0;
		private static int whileIndex = 0;
		private static int ifIndex = 0;

		public final BracketType type;
		public final ArrayList<Instruction> list;
		public final Instruction startPointer;
		public final Instruction endPointer;
		public Instruction loopPointer;
		public Instruction checkPointer;
		public Instruction addPointer;

		@Override
		public String toString() { return startPointer.toString(); }

		private Bracket() {
			this.type = Bracket;
			startPointer = Pointer(":bracket_" + bracketIndex + "_start");
			endPointer = Pointer(":bracket_" + bracketIndex++ + "_end");
			list = new ArrayList<>();
		}

		private Bracket(String funcName, int argLen) {
			type = Function;
			startPointer = Pointer(":func_" + funcName + "_" + argLen + "_start");
			endPointer = Pointer(":func_" + funcName + "_" + argLen + "_end");
			list = Utils.listOf(Cell_Return());
		}

		private Bracket(BracketType bt, ArrayList<Instruction> list) {
			this.list = list;
			this.type = bt;
			switch (bt) {
			case Default -> {
				startPointer = Pointer("DEFAULT_LAYER_START");
				endPointer = Pointer("DEFAULT_LAYER_END");
			}
			case For -> {
				startPointer = Pointer(":for_" + forIndex + "_start");
				endPointer = Pointer(":for_" + forIndex + "_end");
				checkPointer = Pointer(":for_" + forIndex + "_check");
				loopPointer = Pointer(":for_" + forIndex + "_loop");
				addPointer = Pointer(":for_" + forIndex++ + "_add");
			}
			case While -> {
				startPointer = Pointer(":while_" + whileIndex + "_start");
				endPointer = Pointer(":while_" + whileIndex + "_end");
			}
			case If -> {
				startPointer = Pointer(":if_" + ifIndex + "_start");
				endPointer = Pointer(":if_" + ifIndex++ + "_end");
			}
			default -> throw new IGB_CL2_Exception();
			}
		}

		public static Bracket _bracket() { return new Bracket(); }

		public static Bracket _default() { return new Bracket(Default, null); }

		public static Bracket _if() { return new Bracket(If, new ArrayList<>()); }

		public static Bracket _for() { return new Bracket(); }

		public static Bracket _while() { return new Bracket(); }

		public static Bracket _func() { return new Bracket(); }
	}

	static String[] booleanOperators = new String[] { "==", "!=", ">", "<", ">=", "<=" };

	private final IGB_CL2 cl2;
	private final RAM ram;
	private final Functions funcs;

	Stack<Bracket> bracketStack;

	public ControlSolver(IGB_CL2 cl2) {
		this.cl2 = cl2;
		ram = cl2.getRAM();
		this.funcs = cl2.getFunctions();
		bracketStack = new Stack<>();
		bracketStack.push(_default());
	}

	private Bracket pop() {
		Bracket bt = bracketStack.pop();
		if(bt.type == Default)
			throw new IGB_CL2_Exception("Too many brackets.");
		return bt;
	}

	private void push(Bracket bt) { bracketStack.push(bt); }

	private Bracket peek() { return bracketStack.peek(); }

	public void checkStack(String fileName) {
		if(bracketStack.size() != 1)
			throw new IGB_CL2_Exception(true, "\nFile: " + fileName + "\nToo little brackets.");
	}

	public void lowerBracketCounter() { bracketIndex--; }

	private Bracket getBracket(int depth) {
		assert depth > -1;
		if(depth == 0)
			throw new IGB_CL2_Exception("Cannot reach through 0 layers.");
		final int stackSize = bracketStack.size();
		final int index = stackSize - depth;
		if(index <= 0)
			throw new IGB_CL2_Exception("Cannot reach that many layers of brackets.");
		return bracketStack.get(index);
	}

	private Bracket lookFor(int amount, Set<BracketType> set) {
		assert amount > 0;
		int found = 0;
		for (int i = bracketStack.size(); i >= 1; i--) {
			Bracket br = bracketStack.get(i);
			if(set.contains(br.type)) {
				if(found++ == amount)
					return br;
			}
		}
		throw new IGB_CL2_Exception("Didn't find " + amount + " layers of type: " + set);
	}

	private Bracket nextAdd = new Bracket();

	public ArrayList<Instruction> cmd(String cmd) {
		//System.out.println(bracketStack);
		if(cmd.equals("{")) {
			push(nextAdd);
			ArrayList<Instruction> list = Utils.listOf(nextAdd.startPointer);
			nextAdd = new Bracket();
			return list;
		} else if(cmd.equals("}")) {
			Bracket b = pop();
			b.list.add(b.endPointer);
			return b.list;
		} else if(cmd.startsWith("break")) {
			return _break(cmd.substring(5).strip());
		} else if(cmd.startsWith("redo")) {
			return _redo(cmd.substring(4).strip());
		} else if(cmd.equals("return")) {
			return _return();
		} else if(cmd.startsWith("continue")) {
			return _continue(cmd.substring(8).strip());
		} else if(cmd.startsWith("if")) {
			return _if(cmd.substring(2).strip());
		} else if(cmd.startsWith("while")) {

		} else if(cmd.startsWith("for")) {

		}

		return null;
	}

	private ArrayList<Instruction> _if(String rest) {

		return solveIfEq(rest, ":susibaka");
	}

	private ArrayList<Instruction> solveIfEq(String eq, String pointerToJump) {
		ArrayList<Instruction> list = new ArrayList<>();
		int index = -1;
		String ope = null;
		for (String ope1 : booleanOperators) {
			index = eq.indexOf(ope1);
			if(index != -1) {
				ope = ope1;
				break;
			}
		}
		if(index == -1)
			throw new IGB_CL2_Exception("Unknown boolean operator in: \"" + eq + "\".");
		String f1 = eq.substring(0, index).strip();
		String f2 = eq.substring(index + 1).strip();

		System.out.println(f1 + "|" + ope + "|" + f2);

		return list;
	}

	private ArrayList<Instruction> _continue(String rest) {
		int count = 1;
		if(!rest.isBlank()) {
			double val = ram.solveFinalEq(rest);
			if(val % 1 != 0)
				throw new IGB_CL2_Exception("Continure count has to be an integer.");
			count = (int) val;
		}
		Bracket br = lookFor(count, Set.of(For, While));

		return Utils.listOf(Cell_Jump(br.type == For ? br.addPointer.argS[0] : br.startPointer.argS[0]));

	}

	private ArrayList<Instruction> _return() {
		try {
			lookFor(1, Set.of(Function));
		} catch (Exception e) {
			throw new IGB_CL2_Exception("Cannot return outside of functions.");
		}
		return Utils.listOf(Cell_Return());
	}

	private ArrayList<Instruction> _break(String rest) {
		int index = 1;
		if(!rest.isBlank()) {
			double val = ram.solveFinalEq(rest);
			if(val % 1 != 0)
				throw new IGB_CL2_Exception("Break count has to be an integer.");
			index = (int) val;
		}
		Bracket br = getBracket(index);
		return Utils.listOf(Cell_Jump(br.endPointer.argS[0]));
	}

	private ArrayList<Instruction> _redo(String rest) {
		int index = 1;
		if(!rest.isBlank()) {
			double val = ram.solveFinalEq(rest);
			if(val % 1 != 0)
				throw new IGB_CL2_Exception("Redo count has to be an integer.");
			index = (int) val;
		}
		Bracket br = getBracket(index);
		return Utils.listOf(Cell_Jump(br.startPointer.argS[0]));
	}

}
