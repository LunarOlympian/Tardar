# Overview
Tardar is a bot for the board game Tarati. Tarati was created by George Spencer-Brown, the author of _Laws of Form_ (1969) and involves moving pieces around a board where moving to a space captures adjacent pieces. Tardar was created for a school project in an attempt to explore the usage of computer engines when playing Tarati.

# Algorithms used
Tardar uses a few core algorithms to enable it to search upwards of 14 moves into the future, these include:

- Minimax search
- Alpha-Beta pruning
- Depth limited search

These and careful memory management allow Tardar to win essentially every time on Expert and AGI difficulties. Please note that Tardar will slow down quite a bit when Rocs start being introduced, though it should only take around a minute to run on Expert difficulty.
However, **Tardar is primarily designed to be run on Expert difficulty**, with AGI just existing to max out its potential. If you want a challenge play on Expert, if you want the best possible move from Tardar run a turn on AGI. Easy, medium, and hard difficulties still provide a challenge, though due to the design of Tardar these difficulties are very good at finding wins but not as good at verifying the safety of a move, so their difficulty names may not be quite accurate.

# Running
You can get the latest version from the releases tab on the right. Tardar doesn't require any arguments to run. While playing keep an eye on the console. Tardar prints to the console regularly when running, and you can get a lot of info based off of what it prints. The main messages are:

- Beginning move processing - Pretty self-explanatory
- Move timer is ____ minutes - Also self-explanatory. Tardar (should) just send the current best move if it ever goes over this time limit.
- First choice move info: ______ - The first value is the "safety score" which is not used in decision-making, but should give a general idea of how confident Tardar is in its game state (higher is better). Second is the move score.
- Skipped! - Tardar runs 2 searches on expert and AGI difficulty. The first is referred to as the "short-search" and exists to find any guaranteed wins and order the moves based off of how good they are. The second is the "full-search" which searches extremely deep and confirms if a move is safe to make. If the full-search fails then "Skipped!" is printed to the console.
- Move processing complete. - If this is printed by itself then Tardar's chosen move didn't have anything unique to it. However, there are a few follow-ups to it.
    - (Some taunt such as "I was told this would be a challenge." or "ERROR: VICTORY TOO EASY") - These signal that Tardar has found a guaranteed win. When you see these you might as well restart as you're doomed. I wrote several dozen of these, so hopefully they're entertaining!
    - Up the difficulty, coward! - If you put Tardar in a guaranteed loss state, and it is not on AGI difficulty then it will print this.
    - The game was rigged against me! - If you put Tardar in a guaranteed loss state, and it is on AGI difficulty then it will print this.
    - Only one choice. - Tardar only has one legal move.
-  Pruned them all! - Tardar is guaranteed to lose if you make the right moves, though this may be far into the future.
- Checked ____ nodes! - Just a fun little message to see how many board states Tardar has checked. No practical purpose.
- There are one or two remaining ones, but they're self-explanatory and extremely rare.

Pretty sure that's all! Hope you enjoy losing to Tardar >:)