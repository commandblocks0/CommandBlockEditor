const NUMBER_REGEX = /-?\d+(\.\d+)?/g

function clean(n) {
    return Number(n.toPrecision(15));
}

function toCommands(editor) {
    function expand(editor) {
        const lines = [];

        for (const line of editor) {

            const split = line.split(":");

            const repeat =
                Number(split[0].match(/\d+/)?.[0] ?? 1);

            for (let i = 0; i < repeat; i++) {
                lines.push(
                    line.replaceAll(
                        /`([^`]+)`/g,
                        (_, expr) => {
                            if (!/^[0-9i+\-*/()\s.]+$/.test(expr))
                                return "";

                            try {
                                return clean(eval(expr));
                            } catch {
                                return "";
                            }
                        }
                    )
                )
            }
        }

        return lines;
    }

    const commands = [];

    for (const [index, line] of expand(editor).entries()) {

        const tokens = line.split(":");

        const command = {
            type: index == 0 ? "impulse" : "chain",
            conditional: false,
            auto: index == 0 ? false : true,
            command: tokens.length>1 ? tokens.slice(1).join(":").trim() : tokens[0].trim()
        };

        if (tokens[0].includes("!") && index == 0)
            command.type = "repeating";

        if (tokens[0].includes("?"))
            command.conditional = true;

        if (tokens[0].includes("@"))
            command.auto = index == 0 ? true : false;

        commands.push(command);
    }

    return commands;
}

function toEditor(commands) {

    const groups = []
    const editor = []

    let current = null;

    for (const command of commands) {

        const numbers =
            command.command.match(NUMBER_REGEX)
            ?.map(Number)
            || [];

        if (current == null) {

            current = {
                command,
                count: 1,
                patterns: null,
                lastNumbers: numbers
            };

            groups.push(current);

            continue;
        }

        const normalize = s =>
            s.replace(NUMBER_REGEX, "");

        if (
            normalize(command.command)
            !=
            normalize(current.command.command)
        ) {

            current = {
                command,
                count: 1,
                patterns: null,
                lastNumbers: numbers
            };

            groups.push(current);

            continue;
        }

        if (current.patterns == null) {

            current.patterns =
                numbers.map((n, i) => ({
                    start:
                        current.lastNumbers[i],

                    diff:
                        clean(n - current.lastNumbers[i])
                }));

            current.count++;
            current.lastNumbers = numbers;

            continue;
        }

        let valid = true;

        for (const [i, n] of numbers.entries()) {

            const pattern =
                current.patterns[i];

            const expected = clean(
                pattern.start +
                current.count * pattern.diff
            );

            if (n != expected) {
                valid = false;
                break;
            }
        }

        if (valid) {

            current.count++;
            current.lastNumbers = numbers;

        } else {

            current = {
                command,
                count: 1,
                patterns: null,
                lastNumbers: numbers
            };

            groups.push(current);
        }
    }

    for (const [index,group] of groups.entries()) {
        const tokens = [""]

        if (group.command.conditional) tokens[0]+="?"
        if (group.command.type=="repeating") tokens[0]+="!"
        if ((group.command.auto && index==0) || (!group.command.auto && index!=0)) tokens[0]+="@"
        if (group.count>1) tokens[0]+= group.count

        let patternIndex = 0;

        const command =
            group.command.command.replace(
                NUMBER_REGEX,
                number => {

                    if (!group.patterns) return number;

                    const pattern = group.patterns[patternIndex++];

                    if (pattern.diff == 0) return pattern.start;

                    if (pattern.start == 0 && pattern.diff == 1) return "`i`";

                    if (pattern.start == 0) return "`i*" + pattern.diff + "`";

                    if (pattern.diff == 1) return "`i+" + pattern.start + "`";

                    return "`" + pattern.start + "+i*" + pattern.diff + "`";
                }
            );

        tokens.push(command);

        editor.push(
            tokens[0]
                ? tokens.join(": ")
                : tokens[1]
        );
    }

    return editor;
}