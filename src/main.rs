#[tokio::main(flavor = "current_thread")]
async fn main() -> anyhow::Result<()> {
    edulink_two::cli::main().await
}
