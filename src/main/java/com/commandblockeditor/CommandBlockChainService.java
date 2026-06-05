package com.commandblockeditor;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CommandBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

public final class CommandBlockChainService {
    private CommandBlockChainService() {}

    public static List<CommandParser.ParsedCommand> read(ServerWorld world, BlockPos rootPos) {
        List<CommandParser.ParsedCommand> commands = new ArrayList<>();
        BlockPos current = rootPos;
        Direction facing = getFacing(world, rootPos);

        while (true) {
            BlockState state = world.getBlockState(current);

            if (!isCommandBlock(state)) {
                break;
            }

            if (!current.equals(rootPos)
                    && !state.isOf(Blocks.CHAIN_COMMAND_BLOCK)) {
                break;
            }

            BlockEntity blockEntity = world.getBlockEntity(current);
            if (!(blockEntity instanceof CommandBlockBlockEntity commandBlockEntity)) {
                break;
            }

            commands.add(new CommandParser.ParsedCommand(
                    getType(state),
                    state.get(CommandBlock.CONDITIONAL),
                    commandBlockEntity.isAuto(),
                    commandBlockEntity.getCommandExecutor().getCommand()
            ));
            current = current.offset(facing);
        }

        return commands;
    }

    public static void write(ServerPlayerEntity player, BlockPos rootPos, String editorText) {
        ServerWorld world = player.getServerWorld();
        Direction facing = getFacing(world, rootPos);
        List<CommandParser.ParsedCommand> commands = CommandParser.toCommands(editorText.lines().toList());

        for (int i = 0; i < commands.size(); i++) {
            BlockPos pos = rootPos.offset(facing, i);
            CommandParser.ParsedCommand command = commands.get(i);
            Block block = getBlockForCommand(command, i);
            BlockState state = block.getDefaultState()
                    .with(CommandBlock.FACING, facing)
                    .with(CommandBlock.CONDITIONAL, command.conditional());

            world.setBlockState(pos, state, Block.NOTIFY_ALL);
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof CommandBlockBlockEntity commandBlockEntity) {
                commandBlockEntity.getCommandExecutor().setCommand(command.command());
                commandBlockEntity.setAuto(command.auto());
                commandBlockEntity.markDirty();
                commandBlockEntity.updateCommandBlock();
            }
        }

        clearOldTail(world, rootPos.offset(facing, commands.size()), facing);
    }

    public static BlockPos findRoot(ServerWorld world, BlockPos start) {
        BlockPos current = start;
        Direction facing = getFacing(world, start);

        while (true) {
            BlockPos previous = current.offset(facing.getOpposite());
            BlockState previousState = world.getBlockState(previous);
            if (!isCommandBlock(previousState) || previousState.get(CommandBlock.FACING) != facing) {
                return current;
            }
            current = previous;
        }
    }

    private static void clearOldTail(ServerWorld world, BlockPos startPos, Direction facing) {
        BlockPos current = startPos;

        while (current.equals(startPos) || world.getBlockState(current).isOf(Blocks.CHAIN_COMMAND_BLOCK)) {
            world.setBlockState(current, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            current = current.offset(facing);
        }
    }

    private static Direction getFacing(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (isCommandBlock(state)) {
            return state.get(CommandBlock.FACING);
        }
        return Direction.UP;
    }

    private static boolean isCommandBlock(BlockState state) {
        return state.getBlock() instanceof CommandBlock;
    }

    private static String getType(BlockState state) {
        if (state.isOf(Blocks.REPEATING_COMMAND_BLOCK)) {
            return "repeating";
        }
        if (state.isOf(Blocks.CHAIN_COMMAND_BLOCK)) {
            return "chain";
        }
        return "impulse";
    }

    private static Block getBlockForCommand(CommandParser.ParsedCommand command, int index) {
        if (index > 0 || "chain".equals(command.type())) {
            return Blocks.CHAIN_COMMAND_BLOCK;
        }
        if ("repeating".equals(command.type())) {
            return Blocks.REPEATING_COMMAND_BLOCK;
        }
        return Blocks.COMMAND_BLOCK;
    }
}
