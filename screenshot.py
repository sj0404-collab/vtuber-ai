#!/usr/bin/env python3
import subprocess
import sys

def take_screenshot(output="screenshot.png"):
    try:
        subprocess.run([
            "python3", "-c",
            f"""
import subprocess
subprocess.run(['chromium', '--headless', '--disable-gpu', '--screenshot={output}', '--window-size=1280,800', 'http://localhost:8080'])
"""
        ], check=True)
        print(f"Screenshot saved to {output}")
    except Exception as e:
        print(f"Error: {e}")
        print("Make sure you have chromium installed and the app running on localhost:8080")

if __name__ == "__main__":
    take_screenshot(sys.argv[1] if len(sys.argv) > 1 else "screenshot.png")
