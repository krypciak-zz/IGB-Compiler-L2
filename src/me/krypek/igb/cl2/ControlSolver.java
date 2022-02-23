package me.krypek.igb.cl2;

import static me.krypek.igb.cl1.Instruction.Cell_Jump;
import static me.krypek.igb.cl1.Instruction.Cell_Return;
import static me.krypek.igb.cl1.Instruction.*;
import static me.krypek.igb.cl2.BracketType.*;

import java.util.ArrayList;
import java.util.Set;
import java.util.Stack;

import me.krypek.igb.cl1.Instruction;
import me.krypek.utils.Utils;

enum BracketType {
	Default, None, If, For, While, Function
}

class ControlSolver {
	static class Bracket {
		private static int bracketIndex = 0;
		private static int forIndex = 0;
		private static int whileIndex = 0;
		private static int ifIndex = 0;

		public final BracketType type;
		public final ArrayList<Instruction> startList;
		public final ArrayList<Instruction> endList;
		public final Instruction startPointer;
		public final Instruction endPointer;
		public Instruction loopPointer;
		public Instruction checkPointer;
		public Instruction addPointer;

		@Override
		public String toString() { return startPointer.toString(); }

		private Bracket() {
			this.type = None;
			startPointer = Pointer(":bracket_" + bracketIndex + "_start");
			endPointer = Pointer(":bracket_" + bracketIndex + "_end");
			startList = new ArrayList<>();
			endList = new ArrayList<>();
		}

		public void accessBracket() { bracketIndex++; }

		private Bracket(String funcName, int argLen) {
			type = Function;
			startPointer = Pointer(":func_" + funcName + "_" + argLen + "_start");
			endPointer = Pointer(":func_" + funcName + "_" + argLen + "_end");
			startList = new ArrayList<>();
			endList = Utils.listOf(Cell_Return());
		}

		private Bracket(BracketType bt, ArrayList<Instruction> startList, ArrayList<Instruction> endList) {
			this.startList = startList;
			this.endList = endList;
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
				loopPointer = Pointer(":while_" + whileIndex + "_loop");
				checkPointer = Pointer(":while_" + whileIndex++ + "_check");
			}
			case If -> {
				startPointer = Pointer(":if_" + ifIndex + "_start");
				endPointer = Pointer(":if_" + ifIndex++ + "_end");
			}
			default -> throw new IGB_CL2_Exception();
			}
		}

		public static Bracket _bracket() { return new Bracket(); }

		public static Bracket _default() { return new Bracket(Default, null, null); }

		public static Bracket _if(ArrayList<Instruction> startList, ArrayList<Instruction> endList) { return new Bracket(If, startList, endList); }

		public static Bracket _for(ArrayList<Instruction> startList, ArrayList<Instruction> endList) { return new Bracket(For, startList, endList); }

		public static Bracket _while(ArrayList<Instruction> startList, ArrayList<Instruction> endList) { return new Bracket(While, startList, endList); }

		public static Bracket _func() { return new Bracket(); }
	}

	static String[] booleanOperators = new String[] { "==", "!=", ">=", "<=", ">", "<", };

	private final IGB_CL2 cl2;
	private final RAM ram;

	Stack<Bracket> bracketStack;

	public ControlSolver(IGB_CL2 cl2) {
		this.cl2 = cl2;
		ram = cl2.getRAM();
		// this.funcs = cl2.getFunctions();
		bracketStack = new Stack<>();
		bracketStack.push(Bracket._default());
	}

	private Bracket pop() {
		Bracket bt = bracketStack.pop();
		if(bt.type == Default)
			throw new IGB_CL2_Exception("Too many brackets.");
		return bt;
	}

	private void push(Bracket bt) { bracketStack.push(bt); }

	public void checkStack(String fileName) {
		if(bracketStack.size() != 1)
			throw new IGB_CL2_Exception(true, "\nFile: " + fileName + "\nToo little brackets.");
	}

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
		for (int i = bracketStack.size() - 1; i >= 1; i--) {
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
		// System.out.println(bracketStack);
		if(cmd.equals("{")) {
			push(nextAdd);
			if(nextAdd.type == None)
				nextAdd.accessBracket();
			ArrayList<Instruction> list = Utils.listOf(nextAdd.startPointer);
			list.addAll(nextAdd.startList);
			nextAdd = new Bracket();
			return list;
		} else if(cmd.equals("}")) {
			Bracket b = pop();
			b.endList.add(b.endPointer);
			return b.endList;
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
			_while(cmd.substring(5).strip());
		} else if(cmd.startsWith("for")) {

		}

		return null;
	}

	private ArrayList<Instruction> _while(String rest) {
		if(!(rest.startsWith("(") && rest.endsWith(")")))
			throw new IGB_CL2_Exception("While statement has to start with '(' and end with ')'.");

		nextAdd = Bracket._while(new ArrayList<>(), new ArrayList<>());
		nextAdd.startList.addAll(solveIfEq(rest.substring(1, rest.length() - 1), nextAdd.endPointer.argS[0], false));
		nextAdd.startList.add(nextAdd.loopPointer);
		nextAdd.endList.add(nextAdd.checkPointer);
		nextAdd.endList.addAll(solveIfEq(rest.substring(1, rest.length() - 1), nextAdd.loopPointer.argS[0], true));
		return new ArrayList<>();
	}

	private ArrayList<Instruction> _if(String rest) {
		if(!(rest.startsWith("(") && rest.endsWith(")")))
			throw new IGB_CL2_Exception("If statement has to start with '(' and end with ')'.");

		nextAdd = Bracket._if(new ArrayList<>(), new ArrayList<>());
		nextAdd.startList.addAll(solveIfEq(rest.substring(1, rest.length() - 1), nextAdd.endPointer.argS[0], false));

		return new ArrayList<>();
	}

	private ArrayList<Instruction> solveIfEq(String eq, String pointerToJump, boolean revertOperator) {
		if(eq.equals("false") || eq.equals("0"))
			return Utils.listOf(Cell_Jump(pointerToJump));
		if(eq.equals("true") || eq.equals("1"))
			return new ArrayList<>();

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

		if(revertOperator)
			ope = revertOperator(ope);

		String f1 = eq.substring(0, index).strip();
		String f2 = eq.substring(index + ope.length()).strip();

		var t1 = cl2.getEqSolver().solve(f1);
		boolean nc1 = t1.getValue1() == null;
		list.addAll(t1.getValue3());

		var t2 = cl2.getEqSolver().solve(f2);
		boolean nc2 = t2.getValue1() == null;
		if(nc1 && nc2 && t2.getValue2() == t1.getValue2()) {
			t2.setValue2(ram.IF_TEMP2);
			list.addAll(cl2.getEqSolver().solve(f2, ram.IF_TEMP2));
		} else
			list.addAll(t2.getValue3());

		if(!nc1 && !nc2) {
			double val1 = t1.getValue1();
			double val2 = t2.getValue1();
			if(switch (ope) {
			case "==" -> val1 == val2;
			case "!=" -> val1 != val2;
			case ">=" -> val1 >= val2;
			case "<=" -> val1 <= val2;
			case ">" -> val1 > val2;
			case "<" -> val1 < val2;
			default -> throw new IGB_CL2_Exception();
			}) {
				return new ArrayList<>();
			} else
				return Utils.listOf(Cell_Jump(pointerToJump));
		}

		if(!nc1) {
			list.add(If(revertOperator(ope), t2.getValue2(), false, t1.getValue1(), pointerToJump));
		} else
			list.add(If(ope, t1.getValue2(), nc2, nc2 ? t2.getValue2() : t2.getValue1(), pointerToJump));
		return list;
	}

	private String revertOperator(String ope) {
		return switch (ope) {
		case "==", "!=" -> ope;
		case ">" -> "<";
		case "<" -> ">";
		case ">=" -> "<=";
		case "<=" -> ">=";
		default -> throw new IGB_CL2_Exception();
		};
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
