package me.krypek.igb.cl2.solvers;

import static me.krypek.igb.cl1.Instruction.Cell_Jump;
import static me.krypek.igb.cl1.Instruction.Cell_Return;
import static me.krypek.igb.cl1.Instruction.If;
import static me.krypek.igb.cl1.Instruction.Pointer;
import static me.krypek.igb.cl2.solvers.BracketType.*;

import java.util.ArrayList;
import java.util.Set;
import java.util.Stack;

import me.krypek.igb.cl1.Instruction;
import me.krypek.igb.cl2.IGB_CL2;
import me.krypek.igb.cl2.IGB_CL2_Exception;
import me.krypek.igb.cl2.RAM;
import me.krypek.igb.cl2.datatypes.Field;
import me.krypek.igb.cl2.datatypes.Function;
import me.krypek.utils.Utils;

enum BracketType {
	Default, None, If, For, While, Function
}

public class ControlSolver {
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

		public Runnable onBracket = () -> {};

		@Override
		public String toString() { return startPointer.toString(); }

		private Bracket() {
			type = None;
			startPointer = Pointer(":bracket_" + bracketIndex + "_start");
			endPointer = Pointer(":bracket_" + bracketIndex + "_end");
			startList = new ArrayList<>();
			endList = new ArrayList<>();
		}

		public void accessBracket() { bracketIndex++; }

		private Bracket(Function func) {
			type = Function;
			startPointer = Pointer(func.startPointerName);
			endPointer = Pointer(func.endPointerName);
			startList = new ArrayList<>();
			endList = Utils.listOf(Cell_Return());
		}

		private Bracket(BracketType bt, ArrayList<Instruction> startList, ArrayList<Instruction> endList) {
			this.startList = startList;
			this.endList = endList;
			type = bt;
			switch (bt) {
			case Default -> {
				startPointer = Pointer("DEFAULT_LAYER_START");
				endPointer = Pointer("DEFAULT_LAYER_END");
			}
			case For -> {
				startPointer = Pointer(":for_" + forIndex + "_start");
				endPointer = Pointer(":for_" + forIndex + "_end");
				checkPointer = Pointer(":for_" + forIndex + "_check");
				loopPointer = Pointer(":for_" + forIndex++ + "_loop");
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

		public static Bracket _if() { return new Bracket(If, new ArrayList<>(), new ArrayList<>()); }

		public static Bracket _for() { return new Bracket(For, new ArrayList<>(), new ArrayList<>()); }

		public static Bracket _while() { return new Bracket(While, new ArrayList<>(), new ArrayList<>()); }

		public static Bracket _func(Function func) { return new Bracket(func); }
	}

	static String[] booleanOperators = { "==", "!=", ">=", "<=", ">", "<", };

	private final IGB_CL2 cl2;
	private final RAM ram;
	private final EqSolver eqsolver;

	Stack<Bracket> bracketStack;

	public ControlSolver(IGB_CL2 cl2) {
		this.cl2 = cl2;
		ram = cl2.getRAM();
		eqsolver = cl2.getEqSolver();
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
		System.out.println(bracketStack);
		int found = 0;
		for (int i = bracketStack.size() - 1; i >= 1; i--) {
			Bracket br = bracketStack.get(i);
			if(set.contains(br.type))
				if(++found == amount)
					return br;
		}
		throw new IGB_CL2_Exception("Didn't find " + amount + " layers of type: " + set);
	}

	private Bracket nextAdd = new Bracket();

	public ArrayList<Instruction> cmd(String cmd) {
		// System.out.println(bracketStack);
		if(cmd.equals("{")) {
			ram.next();
			push(nextAdd);
			if(nextAdd.type == None)
				nextAdd.accessBracket();
			nextAdd.onBracket.run();

			ArrayList<Instruction> list = Utils.listOf(nextAdd.startPointer);
			list.addAll(nextAdd.startList);
			nextAdd = new Bracket();
			return list;
		}
		if(cmd.equals("}")) {
			ram.pop();
			Bracket b = pop();
			b.endList.add(b.endPointer);
			return b.endList;
		} else if(cmd.startsWith("break"))
			return _break(cmd.substring(5).strip());
		else if(cmd.startsWith("redo"))
			return _redo(cmd.substring(4).strip());
		else if(cmd.equals("return"))
			return _return();
		else if(cmd.startsWith("continue"))
			return _continue(cmd.substring(8).strip());
		else if(cmd.startsWith("if"))
			return _if(cmd.substring(2).strip());
		else if(cmd.startsWith("while"))
			return _while(cmd.substring(5).strip());
		else if(cmd.startsWith("for"))
			return _for(cmd.substring(3).strip());
		else {
			int spaceIndex = cmd.indexOf(' ');
			if(spaceIndex != -1) {
				String first = cmd.substring(0, spaceIndex).strip();
				if(first.equals("void") || IGB_CL2.varStr.contains(first))
					return _function(cmd.substring(spaceIndex).strip());
			} else if(cmd.contains("("))
				try {
					Field funcField = eqsolver.stringToField(cmd, false);
					if(funcField.isFunction())
						return funcField.funcCall.getCall(eqsolver);
				} catch (Exception e) {}
		}

		return null;
	}

	private ArrayList<Instruction> _function(String rest) {
		int bracketIndex = rest.indexOf('(');
		if(bracketIndex == -1)
			throw new IGB_CL2_Exception();
		String name = rest.substring(0, bracketIndex);
		int argsLen = Utils.getArrayElementsFromString(rest.substring(bracketIndex - 1), '(', ')', ",").length;
		Function func = cl2.getFunctions().getFunction(name, argsLen);

		nextAdd = Bracket._func(func);
		nextAdd.onBracket = () -> { func.initVariables(ram); };
		return new ArrayList<>();
	}

	private ArrayList<Instruction> _for(String rest) {
		if((!rest.startsWith("(") || !rest.endsWith(")")))
			throw new IGB_CL2_Exception("While statement has to start with '(' and end with ')'.");

		rest = rest.substring(1, rest.length() - 1).strip();

		String[] split = rest.split(";");
		if(split.length != 3)
			throw new IGB_CL2_Exception("For requires 3 fields.");
		String init = split[0].strip();
		String condi = split[1].strip();
		String addi = split[2].strip();
		nextAdd = Bracket._for();
		ArrayList<Instruction> startList = nextAdd.startList;
		ArrayList<Instruction> initSolved = cl2.getVarSolver().cmd(init, false);
		if(initSolved == null)
			throw new IGB_CL2_Exception("Syntax error at for init field.");
		startList.addAll(initSolved);
		startList.addAll(solveIfEq(condi, nextAdd.endPointer.argS[0], false));
		startList.add(nextAdd.loopPointer);

		ArrayList<Instruction> endList = nextAdd.endList;
		endList.add(nextAdd.checkPointer);
		ArrayList<Instruction> addiSolved = cl2.getVarSolver().cmd(addi, true);
		if(addiSolved == null)
			throw new IGB_CL2_Exception("Syntax error at for addition field.");
		endList.addAll(addiSolved);
		endList.addAll(solveIfEq(condi, nextAdd.loopPointer.argS[0], true));

		return new ArrayList<>();
	}

	private ArrayList<Instruction> _while(String rest) {
		if((!rest.startsWith("(") || !rest.endsWith(")")))
			throw new IGB_CL2_Exception("While statement has to start with '(' and end with ')'.");

		rest = rest.substring(1, rest.length() - 1).strip();

		nextAdd = Bracket._while();
		nextAdd.startList.addAll(solveIfEq(rest, nextAdd.endPointer.argS[0], false));
		nextAdd.startList.add(nextAdd.loopPointer);
		nextAdd.endList.add(nextAdd.checkPointer);
		nextAdd.endList.addAll(solveIfEq(rest, nextAdd.loopPointer.argS[0], true));
		return new ArrayList<>();
	}

	private ArrayList<Instruction> _if(String rest) {
		if((!rest.startsWith("(") || !rest.endsWith(")")))
			throw new IGB_CL2_Exception("If statement has to start with '(' and end with ')'.");

		rest = rest.substring(1, rest.length() - 1).strip();

		nextAdd = Bracket._if();
		nextAdd.startList.addAll(solveIfEq(rest, nextAdd.endPointer.argS[0], false));

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

		var t1 = eqsolver.solve(f1);
		boolean nc1 = t1.getValue1() == null;
		list.addAll(t1.getValue3());

		var t2 = eqsolver.solve(f2);
		boolean nc2 = t2.getValue1() == null;
		if(nc1 && nc2 && t2.getValue2() == t1.getValue2()) {
			t2.setValue2(ram.IF_TEMP2);
			list.addAll(eqsolver.solve(f2, ram.IF_TEMP2));
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
			})
				return new ArrayList<>();
			return Utils.listOf(Cell_Jump(pointerToJump));
		}

		if(!nc1)
			list.add(If(revertOperator(ope), t2.getValue2(), false, t1.getValue1(), pointerToJump));
		else
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

		return Utils.listOf(Cell_Jump(br.type == For ? br.checkPointer.argS[0] : br.startPointer.argS[0]));

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
