<?php declare(strict_types=1);

namespace App\Presenters;

use App\Components\ArticleList;
use App\Model\ArticleFacade;

final class ArticlePresenter
{
	public function __construct(private ArticleFacade $articles)
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
