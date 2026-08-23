# Deck

Markdown-driven slides via Slidev. Edit `slides.md`, nothing else.

    slidev deck/slides.md          # live preview at localhost:3030
    slidev export deck/slides.md   # -> PDF, the format to submit
    slidev build deck/slides.md    # -> static site

Presenter view is at /presenter. Speaker notes go in HTML comments at the
bottom of a slide.

Keep the deck in the repo so the agent can edit it with the same prompts
it uses on the code.
