#!/bin/sh
vegeta attack -rate=100 -duration=10s -targets targets.txt -output result.bin
vegeta report -type=text result.bin
