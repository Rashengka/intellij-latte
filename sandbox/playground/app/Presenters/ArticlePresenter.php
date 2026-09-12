<?php declare(strict_types=1);

namespace App\Presenters;

use App\Components\ArticleList;
use App\Model\ArticleFacade;

final class ArticlePresenter
{
	public const DETAIL_DESTINATION = 'Article:detail';

	public function __construct(private ArticleFacade $articles)
	{
	}

	public function handleRefresh(?string $section = null, ?int $page = null): void
	{
	}

	public function renderDefault(): void
	{
	}

	public function renderDetail(int $id): void
	{
	}

	public function actionArchive(int $year): void
	{
	}

	protected function createComponentArticleList(): ArticleList
	{
		return new ArticleList();
	}
}
