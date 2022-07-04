package me.krypek.igb.cl2.datatypes;

import static me.krypek.igb.cl1.datatypes.Instruction.Cell_Return;
import static me.krypek.igb.cl1.datatypes.Instruction.Pointer;
import static me.krypek.igb.cl2.datatypes.BracketType.*;

import java.util.ArrayList;

import me.krypek.igb.cl2.IGB_CL2_Exception.Err;
import me.krypek.igb.cl1.datatypes.Instruction;
import me.krypek.igb.cl2.PointerNames;
import me.krypek.igb.cl2.datatypes.function.UserFunction;
import me.krypek.utils.Utils;

public class Bracket {
	private static int bracketIndex = 0;
	private static int forIndex = 0;
	private static int whileIndex = 0;
	private static int ifIndex = 0;
	public static int elseIfIndex = 0;

	public static String getElseIfPointer() { return PointerNames.elseIf(elseIfIndex); }

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

	private Bracket(ArrayList<Instruction> startList, ArrayList<Instruction> endList) {
		type = None;
		startPointer = Pointer(PointerNames.bracketStart(bracketIndex));
		endPointer = Pointer(PointerNames.bracketEnd(bracketIndex));
		this.startList = startList;
		this.endList = endList;
	}

	public void accessBracket() { bracketIndex++; }

	private Bracket(UserFunction func) {
		type = Function;
		startPointer = Pointer(func.startPointer);
		endPointer = Pointer(func.endPointer);
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
			startPointer = Pointer(PointerNames.forStart(forIndex));
			endPointer = Pointer(PointerNames.forEnd(forIndex));
			checkPointer = Pointer(PointerNames.forCheck(forIndex));
			loopPointer = Pointer(PointerNames.forLoop(forIndex));
			forIndex++;
		}
		case While -> {
			startPointer = Pointer(PointerNames.whileStart(whileIndex));
			endPointer = Pointer(PointerNames.whileEnd(whileIndex));
			checkPointer = Pointer(PointerNames.whileCheck(whileIndex));
			loopPointer = Pointer(PointerNames.whileLoop(whileIndex));
			whileIndex++;
		}
		case If -> {
			startPointer = Pointer(PointerNames.ifStart(ifIndex));
			endPointer = Pointer(PointerNames.ifEnd(ifIndex));
			ifIndex++;
		}
		default -> throw Err.notPossible();
		}
	}

	public static Bracket _none() { return new Bracket(new ArrayList<>(), new ArrayList<>()); }

	public static Bracket _none(ArrayList<Instruction> startList, ArrayList<Instruction> endList) { return new Bracket(startList, endList); }

	public static Bracket _default() { return new Bracket(Default, null, null); }

	public static Bracket _if() { return new Bracket(If, new ArrayList<>(), new ArrayList<>()); }

	public static Bracket _for() { return new Bracket(For, new ArrayList<>(), new ArrayList<>()); }

	public static Bracket _while() { return new Bracket(While, new ArrayList<>(), new ArrayList<>()); }

	public static Bracket _func(UserFunction func) { return new Bracket(func); }
}
